package com.upc.imageselector.service;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.model.ImageInfo;
import com.upc.imageselector.service.source.FileImageLinkSource;
import com.upc.imageselector.util.FilenameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class DownloadService {

    private final AppProperties props;
    private final FilenameParser parser;

    /**
     * Reads URLs from the configured link file and delegates to {@link #downloadUrls}.
     */
    public List<ImageInfo> downloadAll(BiConsumer<Integer, Integer> progressCallback) throws IOException {
        List<String> urls = new FileImageLinkSource(Path.of(props.getImagesLinkFile())).loadUrls();
        log.info("Found {} URLs in {}", urls.size(), props.getImagesLinkFile());
        return downloadUrls(urls, progressCallback);
    }

    /**
     * Downloads each URL in the supplied list concurrently and returns
     * one {@link ImageInfo} per URL.
     *
     * @param urls             URLs to download
     * @param progressCallback called after each completion with (downloaded, failed) counts; may be null
     */
    public List<ImageInfo> downloadUrls(List<String> urls,
                                        BiConsumer<Integer, Integer> progressCallback) throws IOException {
        Path downloadDir = Path.of(props.getDownloadDir());
        Files.createDirectories(downloadDir);

        AtomicInteger downloaded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(props.getDownloadThreads());
        List<Future<ImageInfo>> futures = new ArrayList<>();

        for (String url : urls) {
            futures.add(executor.submit(() -> {
                ImageInfo info = downloadOne(url, downloadDir);
                if (info.isDownloadFailed()) {
                    failed.incrementAndGet();
                } else {
                    downloaded.incrementAndGet();
                }
                if (progressCallback != null) {
                    progressCallback.accept(downloaded.get(), failed.get());
                }
                return info;
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<ImageInfo> results = new ArrayList<>();
        for (Future<ImageInfo> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException | InterruptedException e) {
                log.error("Download task error: {}", e.getMessage());
            }
        }

        log.info("Download complete: {} ok, {} failed", downloaded.get(), failed.get());
        return results;
    }

    private ImageInfo downloadOne(String url, Path downloadDir) {
        Optional<FilenameParser.ParsedFilename> parsed = parser.parse(url);
        if (parsed.isEmpty()) {
            log.warn("Cannot parse filename from URL, skipping: {}", url);
            return ImageInfo.builder()
                    .url(url)
                    .downloadFailed(true)
                    .downloadError("Filename pattern not found in URL")
                    .build();
        }

        FilenameParser.ParsedFilename pf = parsed.get();
        String filename = pf.canonicalFilename();
        Path target = downloadDir.resolve(filename);

        if (Files.exists(target) && isNonEmpty(target)) {
            log.debug("Already exists, skipping download: {}", filename);
            return buildInfo(pf, url, target, false, null);
        }

        try {
            URL parsedUrl = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) parsedUrl.openConnection();
            conn.setConnectTimeout(props.getDownloadTimeoutSeconds() * 1000);
            conn.setReadTimeout(props.getReadTimeoutSeconds() * 1000);
            conn.setRequestProperty("User-Agent", "UPC-Image-Selector/1.0");
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return fail(pf, url, "HTTP " + status);
            }

            long contentLength = conn.getContentLengthLong();
            if (contentLength > props.getMaxImageSizeBytes()) {
                return fail(pf, url, "File too large: " + contentLength + " bytes");
            }

            try (InputStream is = conn.getInputStream();
                 OutputStream os = Files.newOutputStream(target,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                long copied = is.transferTo(os);
                log.debug("Downloaded {} ({} bytes)", filename, copied);
            }

            return buildInfo(pf, url, target, false, null);

        } catch (Exception e) {
            log.warn("Failed to download {}: {}", url, e.getMessage());
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            return fail(pf, url, e.getMessage());
        }
    }

    private ImageInfo buildInfo(FilenameParser.ParsedFilename pf, String url, Path file,
                                boolean failed, String error) {
        ImageInfo.ImageInfoBuilder b = ImageInfo.builder()
                .filename(pf.canonicalFilename())
                .upc(pf.upc())
                .imageType(pf.imageType())
                .url(url)
                .localPath(file.toAbsolutePath().toString())
                .downloadFailed(failed)
                .downloadError(error);

        if (!failed && Files.exists(file)) {
            try {
                b.fileSizeBytes(Files.size(file));
                BufferedImage img = ImageIO.read(file.toFile());
                if (img != null) {
                    b.width(img.getWidth()).height(img.getHeight());
                }
            } catch (IOException ignored) {}
        }

        return b.build();
    }

    private ImageInfo fail(FilenameParser.ParsedFilename pf, String url, String reason) {
        return ImageInfo.builder()
                .filename(pf != null ? pf.canonicalFilename() : null)
                .upc(pf != null ? pf.upc() : null)
                .imageType(pf != null ? pf.imageType() : null)
                .url(url)
                .downloadFailed(true)
                .downloadError(reason)
                .build();
    }

    private boolean isNonEmpty(Path p) {
        try { return Files.size(p) > 0; } catch (IOException e) { return false; }
    }
}
