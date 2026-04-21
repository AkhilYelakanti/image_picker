# UPC Image Selector

A production-style **Java 21 / Spring Boot 3** application that automatically
selects the best front-of-pack product image for each UPC using fully **local**
heuristics — no cloud APIs, no paid services, no external AI models.

---

## Table of Contents

- [How it works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [REST API](#rest-api)
  - [File-based processing (async)](#file-based-processing-async)
  - [List-based processing (sync)](#list-based-processing-sync)
  - [Results & overrides](#results--overrides)
  - [Exports](#exports)
- [Web UI](#web-ui)
- [Output files](#output-files)
- [Scoring algorithm](#scoring-algorithm)
- [Running tests](#running-tests)
- [Project structure](#project-structure)

---

## How it works

```
ImagesLink.txt  ──or──  POST /api/process/links
         │
         ▼  (concurrent HTTP download, 10 threads by default)
downloaded_images/
         │
         ▼  (per-image scoring — pure Java, no native libs)
         │   • Resolution   (0–20 pts)  pixels / 2 MP, capped at 20
         │   • Sharpness    (0–25 pts)  Laplacian variance
         │   • Brightness   (0–15 pts)  ideal mean ≈ 150 / 255
         │   • Contrast     (0–15 pts)  std-dev of brightness
         │   • Background   (0–15 pts)  white / plain border region
         │   • Centering    (0–10 pts)  foreground COM near image centre
         │   • Type bonus   (0–2  pts)  type 1 > 70 > 74 > 21 (weak)
         ▼
selected_front_images/          ← best image per UPC copied here
selected_front_images.txt       ← plain list of selected filenames
selected_front_images.csv       ← full score breakdown per UPC
results/processing_results.json ← persisted state (survives restarts)
```

Two input modes share the identical download → score → select → persist → export pipeline:

| Mode | Trigger | Response |
|------|---------|----------|
| **File-based** | `POST /api/process` | 202 Accepted — poll `/api/status` |
| **List-based** | `POST /api/process/links` | 200 OK — full result inline |

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |

No native libraries required. Scoring runs in pure Java via `java.awt.image.BufferedImage`
and [Thumbnailator](https://github.com/coobird/thumbnailator) for downsampling.

---

## Quick start

### 1. Clone and build

```bash
git clone https://github.com/AkhilYelakanti/image_picker.git
cd image_picker/upc-image-selector
mvn clean package -DskipTests
```

### 2. Populate `ImagesLink.txt` (file-based mode only)

Place one image URL per line. Each URL must contain a filename matching the pattern
`{14-digit-UPC}_{imageType}.jpg`:

```
https://cdn.example.com/products/00012345678901_1.jpg
https://cdn.example.com/products/00012345678901_70.jpg
https://cdn.example.com/products/00012345678902_74.jpg
# Lines starting with # are ignored
```

### 3. Run

```bash
java -jar target/upc-image-selector-1.0.0.jar
```

The app starts on **http://localhost:8080**.

### 4. Process images

**Option A — Web UI:**
Open http://localhost:8080 and click **Start Processing**.

**Option B — File-based API (async):**
```bash
# Kick off processing
curl -X POST http://localhost:8080/api/process

# Poll until state is COMPLETED or FAILED
curl http://localhost:8080/api/status
```

**Option C — List-based API (sync):**
```bash
curl -X POST http://localhost:8080/api/process/links \
     -H "Content-Type: application/json" \
     -d '{
       "imageLinks": [
         "https://cdn.example.com/products/00012345678901_1.jpg",
         "https://cdn.example.com/products/00012345678901_70.jpg"
       ]
     }'
```
Returns the complete result immediately — no polling required.

### 5. Review & override

Open http://localhost:8080/review to see a thumbnail grid for every UPC.
Click **Select** on any candidate to override the automated choice.
All overrides are persisted and reflected in exports immediately.

---

## Configuration

All settings live in `src/main/resources/application.yml` and can be overridden
via environment variables or `--app.*` command-line arguments.

```yaml
app:
  images-link-file: ImagesLink.txt      # path to URL list (file-based mode)
  download-dir: downloaded_images       # where downloaded images are saved
  selected-dir: selected_front_images   # where selected images are copied
  results-dir: results                  # JSON persistence directory
  download-threads: 10                  # concurrent download threads
  download-timeout-seconds: 30          # HTTP connect timeout
  read-timeout-seconds: 60              # HTTP read timeout
  max-image-size-bytes: 52428800        # 50 MB per-image size limit
  scoring:
    working-size: 600         # downsample long edge to this before scoring
    border-fraction: 0.15     # fraction of image treated as background border
    sharpness-scale: 500.0    # Laplacian variance normalisation factor
    ideal-brightness: 150.0   # target mean brightness (0–255)
    ideal-contrast-low: 40.0  # std-dev lower bound for full contrast score
    ideal-contrast-high: 80.0 # std-dev upper bound for full contrast score
```

Override example at runtime:

```bash
java -jar target/upc-image-selector-1.0.0.jar \
     --app.download-threads=20 \
     --app.download-dir=/data/images
```

---

## REST API

### File-based processing (async)

#### `POST /api/process`

Kicks off the full pipeline using `ImagesLink.txt`. Returns immediately with
`202 Accepted`; poll `/api/status` for progress.

```bash
curl -X POST http://localhost:8080/api/process
```

```json
{
  "message": "Processing started. Poll /api/status for progress."
}
```

Returns `409 Conflict` if processing is already running.

#### `GET /api/status`

```bash
curl http://localhost:8080/api/status
```

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
  "startedAt": "2024-01-15T10:00:00",
  "completedAt": null,
  "errorMessage": null
}
```

Possible `state` values: `IDLE`, `RUNNING`, `COMPLETED`, `FAILED`.

---

### List-based processing (sync)

#### `POST /api/process/links`

Accepts a JSON body with a list of image URLs. Downloads, scores, selects, persists,
and exports — all synchronously. Returns the full result in the response body.

```bash
curl -X POST http://localhost:8080/api/process/links \
     -H "Content-Type: application/json" \
     -d '{
       "imageLinks": [
         "https://cdn.example.com/00012345678901_1.jpg",
         "https://cdn.example.com/00012345678901_70.jpg",
         "https://cdn.example.com/00012345678902_1.jpg"
       ]
     }'
```

```json
{
  "totalLinks": 3,
  "validLinks": 3,
  "invalidLinks": 0,
  "downloadedCount": 2,
  "failedDownloads": 1,
  "totalGroups": 2,
  "failedUrls": [
    "https://cdn.example.com/00012345678901_70.jpg"
  ],
  "groups": {
    "00012345678901": {
      "upc": "00012345678901",
      "selectedFilename": "00012345678901_1.jpg",
      "manualOverride": false,
      "processedAt": "2024-01-15T10:30:00",
      "candidates": [ "..." ]
    },
    "00012345678902": { "..." }
  },
  "processedAt": "2024-01-15T10:30:01"
}
```

**Request validation:**
- `imageLinks` must be present and non-empty → `400 Bad Request` otherwise
- URLs that do not match the `{14-digit-UPC}_{type}.ext` pattern are counted as
  `invalidLinks` and appear in `failedUrls`

**Response fields:**

| Field | Description |
|-------|-------------|
| `totalLinks` | Number of URLs submitted |
| `validLinks` | URLs with a parseable UPC/type filename |
| `invalidLinks` | URLs that could not be parsed (no UPC extracted) |
| `downloadedCount` | Successfully downloaded images |
| `failedDownloads` | Download errors (HTTP errors, timeouts, size limit) |
| `totalGroups` | Distinct UPCs processed |
| `failedUrls` | List of URLs that failed download or parsing |
| `groups` | Map of UPC → full result with candidates and scores |
| `processedAt` | Completion timestamp |

---

### Results & overrides

#### `GET /api/results`

Returns all persisted UPC results.

```bash
curl http://localhost:8080/api/results
```

```json
[
  {
    "upc": "00012345678901",
    "selectedFilename": "00012345678901_1.jpg",
    "manualOverride": false,
    "processedAt": "2024-01-15T10:30:00",
    "candidates": [
      {
        "filename": "00012345678901_1.jpg",
        "imageType": "1",
        "width": 1200,
        "height": 1200,
        "downloadFailed": false,
        "score": {
          "totalScore": 78.5,
          "resolutionScore": 20.0,
          "sharpnessScore": 22.1,
          "brightnessScore": 13.4,
          "contrastScore": 12.8,
          "backgroundScore": 7.2,
          "centeringScore": 1.0,
          "typeTiebreaker": 2.0,
          "selected": true,
          "selectionReason": "Best total score 78.50 among 2 scored candidate(s) (rank #1)"
        }
      }
    ]
  }
]
```

#### `GET /api/results/{upc}`

Single UPC result. Returns `404 Not Found` if the UPC has no recorded result.

```bash
curl http://localhost:8080/api/results/00012345678901
```

#### `POST /api/results/{upc}/override`

Manually override the selected image. The filename must be one of the UPC's
existing candidates.

```bash
curl -X POST http://localhost:8080/api/results/00012345678901/override \
     -H "Content-Type: application/json" \
     -d '{"filename": "00012345678901_70.jpg"}'
```

Returns the updated result with `"manualOverride": true` and an `overriddenAt`
timestamp. Export files are regenerated automatically.

**Error responses:**
- `400 Bad Request` — filename is blank
- `404 Not Found` — UPC not found, or filename is not a candidate for that UPC

---

### Exports

#### `GET /api/export/txt`

Downloads `selected_front_images.txt` as an attachment.

```bash
curl -OJ http://localhost:8080/api/export/txt
```

#### `GET /api/export/csv`

Downloads `selected_front_images.csv` with full score breakdown as an attachment.

```bash
curl -OJ http://localhost:8080/api/export/csv
```

---

## Web UI

| URL | Description |
|-----|-------------|
| `http://localhost:8080/` | Dashboard — start processing, view live progress, quick stats |
| `http://localhost:8080/review` | Thumbnail grid — all UPCs with score badges and Select buttons |
| `http://localhost:8080/review?upc=00012345678901` | Filter review grid by UPC |
| `http://localhost:8080/review/00012345678901` | Detail view for a single UPC |

The dashboard polls `/api/status` every 2 seconds while processing is running and
updates the progress bar in real time. The review page shows a collapsible score
breakdown for every candidate image.

---

## Output files

### `selected_front_images.txt`

```
# Generated by UPC Image Selector
# Generated at: 2024-01-15T10:30:00
# Total UPCs: 3

00012345678901_1.jpg
00012345678902_70.jpg
00099482449362_74.jpg
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
| Width | Pixel width |
| Height | Pixel height |
| FileSizeBytes | File size in bytes |
| ManualOverride | `true` if overridden by user |
| ProcessedAt | Pipeline completion timestamp |
| OverriddenAt | Override timestamp (blank if automatic) |

### `results/processing_results.json`

Full structured state including all candidates, scores, and metadata.
Loaded on startup so results survive application restarts.

---

## Scoring algorithm

All analysis is pure Java pixel math — no native libraries, no cloud calls.

Images are downsampled to a 600 px working size via Thumbnailator before scoring
to keep CPU time predictable regardless of source resolution.

### Resolution (0–20)

```
score = min(width × height / 2_000_000, 1.0) × 20
```

Caps at 2 MP so extremely large images don't dominate.

### Sharpness — Laplacian variance (0–25)

```
kernel = [[ 0,  1,  0],
          [ 1, -4,  1],
          [ 0,  1,  0]]

For each interior pixel (ITU-R BT.601 grayscale):
  response = Laplacian convolution at pixel

score = min(variance(responses) / 500, 1.0) × 25
```

High variance → strong local edges → image is in focus.

### Brightness (0–15)

```
deviation = |mean_brightness - 150| / 120   (clamped to [0, 1])
score = (1 - deviation) × 15
```

Penalises images that are very dark or very over-exposed.

### Contrast (0–15)

```
std_dev of per-pixel brightness:
  < 40         → ramp from 0 to 15
  40–80        → 15  (full score plateau)
  > 80         → decay
```

### Background plainness (0–15)

The outer 15 % border of the image (in each dimension) is sampled.
Each border pixel is converted to HSB colour space:

```
score = ((1 - mean_saturation) × 0.5 + mean_brightness × 0.5) × 15
```

Low saturation + high HSB brightness ≈ white or plain background.

### Subject centering (0–10)

Background brightness is estimated from the four image corners.
Pixels significantly darker than the background are classified as foreground.
The foreground centre-of-mass (COM) distance from the image centre is normalised:

```
score = (1 - normalised_distance) × 10
```

### Type bonus (0–2)

A small tie-breaker that reflects typical image-type conventions:

| Type | Bonus |
|------|-------|
| `1`  | 2.0   |
| `70` | 1.5   |
| `74` | 1.0   |
| `21` | 0.5   |
| other | 0.0  |

---

## Running tests

```bash
# All tests (44 total)
mvn test

# Individual test class
mvn test -Dtest=FilenameParserTest
mvn test -Dtest=ScoringServiceTest
mvn test -Dtest=ProcessingServiceTest
mvn test -Dtest=ApiControllerTest
mvn test -Dtest=LinksProcessingControllerTest

# Verbose output to console
mvn test -Dsurefire.useFile=false
```

Test coverage summary:

| Test class | Tests | What is covered |
|------------|-------|-----------------|
| `FilenameParserTest` | 12 | UPC/type regex parsing, parameterised cases, edge cases |
| `ScoringServiceTest` | 16 | Each scoring component, synthetic BufferedImage I/O |
| `ProcessingServiceTest` | 5 | Override validation, status transitions (Mockito) |
| `ApiControllerTest` | 8 | File-based REST endpoints (`@WebMvcTest`) |
| `LinksProcessingControllerTest` | 3 | List-based REST endpoint (`@WebMvcTest`) |

---

## Project structure

```
src/main/java/com/upc/imageselector/
├── UpcImageSelectorApplication.java      # @SpringBootApplication + @EnableAsync
├── config/
│   ├── AppProperties.java                # @ConfigurationProperties(prefix="app")
│   ├── AsyncConfig.java                  # single-thread processingExecutor bean
│   └── WebConfig.java                    # /images/** and /selected/** static mappings
├── controller/
│   ├── ApiController.java                # REST: process, status, results, override, links
│   ├── ExportController.java             # GET /api/export/txt and /csv
│   └── UiController.java                # Thymeleaf: /, /review, /review/{upc}
├── dto/
│   ├── ImageInfoDto.java
│   ├── ImageLinksRequestDto.java         # POST /api/process/links request body
│   ├── ImageScoreDto.java
│   ├── LinksProcessingResultDto.java     # POST /api/process/links response body
│   ├── OverrideRequestDto.java
│   ├── ProcessingStatusDto.java
│   └── UpcResultDto.java
├── exception/
│   ├── GlobalExceptionHandler.java       # ProblemDetail (RFC 7807) error responses
│   ├── ProcessingException.java
│   └── ResourceNotFoundException.java
├── model/
│   ├── ImageInfo.java
│   ├── ImageScore.java
│   ├── ProcessingResult.java
│   └── ProcessingStatus.java
├── service/
│   ├── source/
│   │   ├── ImageLinkSource.java          # strategy interface: loadUrls()
│   │   ├── FileImageLinkSource.java      # reads ImagesLink.txt
│   │   └── ListImageLinkSource.java      # wraps a caller-supplied List<String>
│   ├── DownloadService.java              # downloadAll() / downloadUrls() concurrent HTTP
│   ├── ExportService.java                # TXT + CSV generation and streaming
│   ├── PersistenceService.java           # JSON read/write with in-memory cache
│   ├── ProcessingService.java            # pipeline orchestration (async + sync paths)
│   └── ScoringService.java               # image heuristics (pure Java pixel math)
└── util/
    └── FilenameParser.java               # regex: (\d{14})_(\w+)\.(jpg|jpeg|png|…)

src/main/resources/
├── application.yml
└── templates/
    ├── index.html                        # Bootstrap 5 dashboard with JS polling
    └── review.html                       # thumbnail grid with score breakdown
```

---

## License

MIT
