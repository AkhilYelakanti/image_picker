# UPC Image Selector — Spring Boot Starter

A **Spring Boot auto-configuration library** that selects the best front-of-pack
product image for each UPC using fully **local** heuristics — no cloud APIs, no
paid services, no external AI models.

Add the JAR as a Maven dependency and the full pipeline (download → score → select
→ persist → export) is auto-configured in your Spring Boot application.

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Adding the dependency](#adding-the-dependency)
- [Quick start — 5 minutes](#quick-start--5-minutes)
- [How it works](#how-it-works)
- [Configuration reference](#configuration-reference)
- [REST API](#rest-api)
- [Web UI](#web-ui)
- [Customising or disabling individual beans](#customising-or-disabling-individual-beans)
- [Output files](#output-files)
- [Scoring algorithm](#scoring-algorithm)
- [Running tests](#running-tests)
- [Project structure](#project-structure)
- [Publishing to Maven Central](#publishing-to-maven-central)

---

## Features

| Feature | Details |
|---------|---------|
| Auto-configuration | Drop the JAR on the classpath — all beans wire up automatically |
| Java 17+ | Runs on Java 17, 21, or any later LTS release |
| Zero native deps | Pure Java pixel math; no OpenCV, no C++ libs |
| Spring Boot 3.2+ | Uses `@AutoConfiguration`, `AutoConfiguration.imports` (Spring Boot 3 standard) |
| Optional REST API | Activated only when `spring-boot-starter-web` is present |
| Optional review UI | Activated only when `spring-boot-starter-thymeleaf` is also present |
| Fully overridable | Every bean is `@ConditionalOnMissingBean` — override anything you need |

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 17 or later |
| Spring Boot | 3.2.x or later |
| Maven | 3.9+ (for builds) |

---

## Adding the dependency

> **Note:** Until the artifact is published to Maven Central, install it locally
> first (`mvn install`), then reference it from your project.

### Maven

```xml
<dependency>
    <groupId>io.github.akhilyelakanti</groupId>
    <artifactId>upc-image-selector-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.akhilyelakanti:upc-image-selector-spring-boot-starter:1.0.0'
```

### Optional starters

The library's REST API and review UI are conditional:

| You want | Add this alongside the starter |
|----------|-------------------------------|
| REST API (`/api/**`) | `spring-boot-starter-web` |
| Review UI (`/review`) | `spring-boot-starter-web` + `spring-boot-starter-thymeleaf` |
| Core services only | Nothing extra needed |

---

## Quick start — 5 minutes

### 1. Create a Spring Boot app

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

Add `spring-boot-starter-web` + `spring-boot-starter-thymeleaf` + this starter to
your `pom.xml`. All REST endpoints and the review UI are auto-configured.

### 2. Create `ImagesLink.txt` in the working directory

```
# One URL per line; filename must match {14-digit-UPC}_{imageType}.jpg
https://cdn.example.com/products/00012345678901_1.jpg
https://cdn.example.com/products/00012345678901_70.jpg
https://cdn.example.com/products/00012345678902_74.jpg
```

### 3. Run

```bash
mvn spring-boot:run
# or
java -jar target/my-app.jar
```

Open **http://localhost:8080** → click **Start Processing** → watch the progress bar.

### 4. Use programmatically (no web required)

```java
@Service
public class MyService {

    private final ProcessingService processingService;

    public MyService(ProcessingService processingService) {
        this.processingService = processingService;
    }

    public void runFromFile() {
        processingService.startProcessing();       // async; poll getStatus()
    }

    public LinksProcessingResultDto runFromList(List<String> urls) throws IOException {
        return processingService.processLinks(urls); // sync; returns full result
    }
}
```

---

## How it works

```
ImagesLink.txt  ──or──  POST /api/process/links
        │
        ▼  (concurrent HTTP download, 10 threads by default)
downloaded_images/
        │
        ▼  (per-image scoring — pure Java pixel math)
        │   • Resolution   (0–20 pts)
        │   • Sharpness    (0–25 pts)  Laplacian variance
        │   • Brightness   (0–15 pts)  ideal mean ≈ 150/255
        │   • Contrast     (0–15 pts)  std-dev of brightness
        │   • Background   (0–15 pts)  white / plain border region
        │   • Centering    (0–10 pts)  foreground COM near image centre
        │   • Type bonus   (0–2  pts)  type 1 > 70 > 74 > 21
        ▼
selected_front_images/          ← best image per UPC copied here
selected_front_images.txt       ← plain list of selected filenames
selected_front_images.csv       ← full score breakdown per UPC
results/processing_results.json ← persisted state (survives restarts)
```

Two input modes share the **identical** pipeline:

| Mode | Trigger | Response |
|------|---------|----------|
| **File-based** | `POST /api/process` | 202 Accepted — poll `/api/status` |
| **List-based** | `POST /api/process/links` | 200 OK — full result inline |

---

## Configuration reference

All properties have sensible defaults in `AppProperties`. Override them in your
application's `application.yml`:

```yaml
app:
  images-link-file: ImagesLink.txt      # path to URL list (file-based mode)
  download-dir: downloaded_images       # downloaded image cache
  selected-dir: selected_front_images   # selected image copies
  results-dir: results                  # JSON persistence directory
  download-threads: 10                  # concurrent download threads
  download-timeout-seconds: 30          # HTTP connect timeout
  read-timeout-seconds: 60              # HTTP read timeout
  max-image-size-bytes: 52428800        # 50 MB per-image limit
  scoring:
    working-size: 600         # downsample long edge before scoring
    border-fraction: 0.15     # fraction of image used as background border
    sharpness-scale: 500.0    # Laplacian variance normalisation factor
    ideal-brightness: 150.0   # target mean brightness (0–255)
    ideal-contrast-low: 40.0  # std-dev lower bound for full contrast score
    ideal-contrast-high: 80.0 # std-dev upper bound for full contrast score
```

Override at runtime:

```bash
java -jar my-app.jar --app.download-threads=20 --app.download-dir=/mnt/images
```

---

## REST API

### File-based processing (async)

#### `POST /api/process`

Starts the pipeline from `ImagesLink.txt`. Returns `202 Accepted` immediately;
poll `/api/status` for progress.

```bash
curl -X POST http://localhost:8080/api/process
```

Returns `409 Conflict` if a run is already in progress.

#### `GET /api/status`

```json
{
  "state": "RUNNING",
  "currentStep": "Scoring and selecting images…",
  "totalUrls": 150,
  "downloadedCount": 143,
  "failedDownloads": 7,
  "totalUpcs": 62,
  "scoredUpcs": 38,
  "progressPercent": 61,
  "startedAt": "2024-01-15T10:00:00"
}
```

States: `IDLE`, `RUNNING`, `COMPLETED`, `FAILED`.

---

### List-based processing (sync)

#### `POST /api/process/links`

Accepts a list of image URLs, runs the full pipeline synchronously, returns the
complete result in the response body.

```bash
curl -X POST http://localhost:8080/api/process/links \
     -H "Content-Type: application/json" \
     -d '{"imageLinks":["https://cdn.example.com/00012345678901_1.jpg"]}'
```

```json
{
  "totalLinks": 1,
  "validLinks": 1,
  "invalidLinks": 0,
  "downloadedCount": 1,
  "failedDownloads": 0,
  "totalGroups": 1,
  "failedUrls": [],
  "groups": { "00012345678901": { "..." } },
  "processedAt": "2024-01-15T10:30:00"
}
```

---

### Results & overrides

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/results` | All UPC results |
| `GET` | `/api/results/{upc}` | Single UPC result; `404` if not found |
| `POST` | `/api/results/{upc}/override` | Override selected image |
| `GET` | `/api/export/txt` | Download `selected_front_images.txt` |
| `GET` | `/api/export/csv` | Download `selected_front_images.csv` |

#### Override example

```bash
curl -X POST http://localhost:8080/api/results/00012345678901/override \
     -H "Content-Type: application/json" \
     -d '{"filename":"00012345678901_70.jpg"}'
```

The filename must be one of the existing candidates for that UPC. Export files are
regenerated automatically after each override.

---

## Web UI

| URL | Description |
|-----|-------------|
| `http://localhost:8080/` | Dashboard — start processing, live progress bar |
| `http://localhost:8080/review` | Thumbnail grid — all UPCs with score badges |
| `http://localhost:8080/review?upc=…` | Filter review to a single UPC |
| `http://localhost:8080/review/{upc}` | Detail view for one UPC |

Templates are served from `classpath:/templates/upc-image-selector/` so they will
not conflict with templates in your application.

---

## Customising or disabling individual beans

Every auto-configured bean uses `@ConditionalOnMissingBean`. Declare your own bean
of the same type to replace it:

```java
@Configuration
public class MyConfig {

    // Replace the scoring service with custom logic
    @Bean
    public ScoringService scoringService(AppProperties props) {
        return new MyScoringService(props);
    }

    // Replace the download directory mapping
    @Bean
    public WebConfig webConfig(AppProperties props) {
        return new WebConfig(props) {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/images/**")
                        .addResourceLocations("file:/custom/path/");
            }
        };
    }
}
```

To disable the review UI entirely, exclude `ThymeleafAutoConfiguration` or simply
do not add `spring-boot-starter-thymeleaf` to your project.

---

## Output files

### `selected_front_images.txt`

```
# Generated by UPC Image Selector
# Generated at: 2024-01-15T10:30:00
# Total UPCs: 3

00012345678901_1.jpg
00012345678902_70.jpg
```

### `selected_front_images.csv`

| Column | Description |
|--------|-------------|
| UPC | 14-digit product code |
| SelectedFilename | Chosen image filename |
| ImageType | Image type code |
| TotalScore | Composite score (max ~102) |
| ResolutionScore | 0–20 |
| SharpnessScore | 0–25 |
| BrightnessScore | 0–15 |
| ContrastScore | 0–15 |
| BackgroundScore | 0–15 |
| CenteringScore | 0–10 |
| Width / Height | Pixel dimensions |
| FileSizeBytes | File size in bytes |
| ManualOverride | `true` if overridden by user |
| ProcessedAt / OverriddenAt | Timestamps |

---

## Scoring algorithm

All analysis is pure Java — no native libraries, no cloud calls. Images are
downsampled to 600 px before scoring for consistent speed.

| Component | Max | Formula summary |
|-----------|-----|-----------------|
| Resolution | 20 | `min(W×H / 2MP, 1) × 20` — caps at 2 MP |
| Sharpness | 25 | Laplacian variance / 500, capped at 25 |
| Brightness | 15 | Linear penalty away from 150/255 |
| Contrast | 15 | Full score for std-dev 40–80; ramp below/above |
| Background | 15 | Border region: `(1−saturation)×0.5 + brightness×0.5` |
| Centering | 10 | Foreground centre-of-mass distance from image centre |
| Type bonus | 2 | 1→2.0, 70→1.5, 74→1.0, 21→0.5 |

---

## Running tests

```bash
# All 44 tests
mvn test

# Individual class
mvn test -Dtest=FilenameParserTest
mvn test -Dtest=ScoringServiceTest
mvn test -Dtest=ProcessingServiceTest
mvn test -Dtest=ApiControllerTest
mvn test -Dtest=LinksProcessingControllerTest
```

---

## Project structure

```
src/main/java/com/upc/imageselector/
├── autoconfigure/
│   └── UpcImageSelectorAutoConfiguration.java   # ← library entry point
├── config/
│   ├── AppProperties.java      # @ConfigurationProperties(prefix="app")
│   ├── AsyncConfig.java        # processingExecutor bean
│   └── WebConfig.java          # /images/** and /selected/** resource handlers
├── controller/
│   ├── ApiController.java      # REST: /api/process, /api/status, /api/results
│   ├── ExportController.java   # GET /api/export/txt, /csv
│   └── UiController.java       # Thymeleaf: /, /review, /review/{upc}
├── dto/                        # Request/response DTOs
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ProcessingException.java
│   └── ResourceNotFoundException.java
├── model/                      # Domain models (ImageInfo, ImageScore, etc.)
├── service/
│   ├── source/
│   │   ├── ImageLinkSource.java          # strategy interface
│   │   ├── FileImageLinkSource.java      # reads ImagesLink.txt
│   │   └── ListImageLinkSource.java      # wraps List<String>
│   ├── DownloadService.java
│   ├── ExportService.java
│   ├── PersistenceService.java
│   ├── ProcessingService.java
│   └── ScoringService.java
└── util/
    └── FilenameParser.java

src/main/resources/
├── META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── static/css/
│   └── upc-image-selector.css           # namespaced to avoid conflicts
└── templates/upc-image-selector/        # namespaced Thymeleaf templates
    ├── index.html
    └── review.html

src/test/java/com/upc/imageselector/
└── TestApplication.java                 # @SpringBootApplication for tests only
```

---

## Publishing to Maven Central

> Follow these steps when you are ready to publish a release publicly.

### Step 1 — Create a Sonatype account

Register at **https://central.sonatype.com** with your GitHub account.

### Step 2 — Verify your namespace

Your `groupId` is `io.github.akhilyelakanti`.

On the **Namespaces** page in Sonatype Central, add `io.github.akhilyelakanti`.
Sonatype will ask you to create a public GitHub repository named exactly as shown
(e.g., `CENTRAL-XXXXXXXX`). Create it, then click **Verify Namespace**. Approval
is instant for GitHub-based namespaces.

### Step 3 — Generate a GPG key

```bash
# Generate a new key (use your real name and email)
gpg --gen-key

# List keys to get your key ID
gpg --list-secret-keys --keyid-format=long

# Publish the public key (pick any keyserver)
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
```

### Step 4 — Configure `~/.m2/settings.xml`

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <!-- Generate a token at central.sonatype.com → Account → Generate User Token -->
      <username>YOUR_TOKEN_USERNAME</username>
      <password>YOUR_TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>release</id>
      <properties>
        <!-- Optional: store GPG passphrase here instead of passing on the CLI -->
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

### Step 5 — Deploy

```bash
# From the upc-image-selector/ directory
mvn clean deploy -P release
```

This will:
1. Compile and run all tests
2. Generate `-sources.jar` and `-javadoc.jar`
3. Sign all artifacts with GPG
4. Upload to Sonatype Central (status: **Uploaded**, not yet published)

### Step 6 — Review and publish

Log into **https://central.sonatype.com → Deployments**.
Find your upload, inspect the validation report, and click **Publish** when ready.
The artifact appears on Maven Central within ~30 minutes.

### Step 7 — Future releases

Bump the version in `pom.xml`, tag the commit, and re-run `mvn clean deploy -P release`.

```bash
# Tag the release
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

---

## License

MIT
