package com.upc.imageselector.service;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.dto.LinksProcessingResultDto;
import com.upc.imageselector.dto.UpcResultDto;
import com.upc.imageselector.exception.ResourceNotFoundException;
import com.upc.imageselector.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessingService {

    private final AppProperties props;
    private final DownloadService downloadService;
    private final ScoringService scoringService;
    private final PersistenceService persistenceService;
    private final ExportService exportService;

    private final AtomicReference<ProcessingStatus> status =
            new AtomicReference<>(new ProcessingStatus());

    // ── status ────────────────────────────────────────────────────────────────

    public ProcessingStatus getStatus() {
        return status.get();
    }

    public boolean isRunning() {
        return status.get().getState() == ProcessingStatus.State.RUNNING;
    }

    // ── file-based async trigger ──────────────────────────────────────────────

    /**
     * Kicks off the full pipeline asynchronously from the configured link file.
     * Throws {@link IllegalStateException} if already running.
     */
    public void startProcessing() {
        ProcessingStatus current = status.get();
        if (current.getState() == ProcessingStatus.State.RUNNING) {
            throw new IllegalStateException("Processing is already in progress");
        }
        ProcessingStatus fresh = new ProcessingStatus();
        fresh.setState(ProcessingStatus.State.RUNNING);
        fresh.setStartedAt(LocalDateTime.now());
        fresh.setCurrentStep("Initialising…");
        status.set(fresh);
        runAsync();
    }

    @Async("processingExecutor")
    void runAsync() {
        ProcessingStatus s = status.get();
        try {
            step(s, "Downloading images…");
            List<ImageInfo> allImages = downloadService.downloadAll((done, fail) -> {
                s.setDownloadedCount(done);
                s.setFailedDownloads(fail);
            });
            s.setTotalUrls(allImages.size());

            step(s, "Grouping by UPC…");
            Map<String, ProcessingResult> results = runPipelineCore(allImages, s);

            s.setState(ProcessingStatus.State.COMPLETED);
            s.setCompletedAt(LocalDateTime.now());
            s.setCurrentStep("Done. Processed " + results.size() + " UPCs.");
            log.info("Processing complete – {} UPCs", results.size());

        } catch (Exception e) {
            log.error("Processing failed: {}", e.getMessage(), e);
            s.setState(ProcessingStatus.State.FAILED);
            s.setErrorMessage(e.getMessage());
            s.setCurrentStep("Failed: " + e.getMessage());
            s.setCompletedAt(LocalDateTime.now());
        }
    }

    // ── list-based synchronous processing ────────────────────────────────────

    /**
     * Synchronously processes the given image URLs through the full pipeline
     * and returns a summary result.
     */
    public LinksProcessingResultDto processLinks(List<String> imageLinks) throws IOException {
        List<ImageInfo> allImages = downloadService.downloadUrls(imageLinks, null);

        int downloadedCount = (int) allImages.stream().filter(i -> !i.isDownloadFailed()).count();
        int failedDownloads = allImages.size() - downloadedCount;
        int validLinks = (int) allImages.stream().filter(i -> i.getUpc() != null).count();
        int invalidLinks = allImages.size() - validLinks;

        List<String> failedUrls = allImages.stream()
                .filter(i -> i.isDownloadFailed())
                .map(ImageInfo::getUrl)
                .filter(Objects::nonNull)
                .toList();

        Map<String, ProcessingResult> results = runPipelineCore(allImages, null);

        Map<String, UpcResultDto> groups = results.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> UpcResultDto.from(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));

        return LinksProcessingResultDto.builder()
                .totalLinks(imageLinks.size())
                .validLinks(validLinks)
                .invalidLinks(invalidLinks)
                .downloadedCount(downloadedCount)
                .failedDownloads(failedDownloads)
                .totalGroups(results.size())
                .failedUrls(failedUrls)
                .groups(groups)
                .processedAt(LocalDateTime.now())
                .build();
    }

    // ── shared pipeline core (steps 2–5) ─────────────────────────────────────

    /**
     * Group → score+select → persist → export. The {@code status} parameter is
     * updated when provided (async path); pass {@code null} for the sync path.
     */
    private Map<String, ProcessingResult> runPipelineCore(List<ImageInfo> allImages,
                                                           ProcessingStatus s) throws IOException {
        // Group by UPC
        Map<String, List<ImageInfo>> byUpc = allImages.stream()
                .filter(i -> i.getUpc() != null)
                .collect(Collectors.groupingBy(ImageInfo::getUpc,
                        LinkedHashMap::new, Collectors.toList()));

        if (s != null) {
            s.setTotalUpcs(byUpc.size());
            log.info("Found {} distinct UPCs", byUpc.size());
            step(s, "Scoring and selecting images…");
        }

        Path selectedDir = Path.of(props.getSelectedDir());
        Files.createDirectories(selectedDir);

        Map<String, ProcessingResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, List<ImageInfo>> entry : byUpc.entrySet()) {
            ProcessingResult result = selectBest(entry.getKey(), entry.getValue(), selectedDir);
            results.put(entry.getKey(), result);
            if (s != null) {
                s.setScoredUpcs(s.getScoredUpcs() + 1);
            }
        }

        if (s != null) step(s, "Persisting results…");
        persistenceService.saveAll(results);

        if (s != null) step(s, "Generating export files…");
        exportService.generateOutputFiles(results);

        return results;
    }

    // ── override ──────────────────────────────────────────────────────────────

    public ProcessingResult overrideSelection(String upc, String filename) {
        ProcessingResult result = persistenceService.getByUpc(upc)
                .orElseThrow(() -> new ResourceNotFoundException("No result for UPC: " + upc));

        boolean candidateExists = result.getCandidates() != null &&
                result.getCandidates().stream().anyMatch(i -> filename.equals(i.getFilename()));
        if (!candidateExists) {
            throw new ResourceNotFoundException(
                    "Filename '" + filename + "' is not a candidate for UPC: " + upc);
        }

        if (result.getCandidates() != null) {
            result.getCandidates().forEach(img -> {
                if (img.getScore() != null) {
                    img.getScore().setSelected(filename.equals(img.getFilename()));
                }
            });
        }

        Path src = Path.of(props.getDownloadDir(), filename);
        Path dst = Path.of(props.getSelectedDir(), filename);
        try {
            if (Files.exists(src)) {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Could not copy override image: {}", e.getMessage());
        }

        result.setSelectedFilename(filename);
        result.setSelectedImagePath(dst.toAbsolutePath().toString());
        result.setManualOverride(true);
        result.setOverriddenAt(LocalDateTime.now());

        result.getCandidates().stream()
                .filter(i -> filename.equals(i.getFilename()))
                .findFirst()
                .ifPresent(img -> {
                    if (img.getScore() != null) {
                        img.getScore().setSelectionReason("Manually selected by user");
                    }
                });

        persistenceService.saveOne(result);

        try {
            exportService.generateOutputFiles(persistenceService.getAll());
        } catch (IOException e) {
            log.warn("Export regeneration failed after override: {}", e.getMessage());
        }

        return result;
    }

    // ── selection logic ───────────────────────────────────────────────────────

    private ProcessingResult selectBest(String upc, List<ImageInfo> candidates, Path selectedDir) {
        List<ImageInfo> scoreable = candidates.stream()
                .filter(i -> !i.isDownloadFailed() && i.getLocalPath() != null)
                .toList();

        for (ImageInfo img : scoreable) {
            ImageScore sc = scoringService.score(Path.of(img.getLocalPath()), img.getImageType());
            img.setScore(sc);
        }

        Optional<ImageInfo> best = scoreable.stream()
                .filter(i -> i.getScore() != null)
                .max(Comparator.comparingDouble(i -> i.getScore().getTotalScore()));

        String selectedFilename = null;
        Path selectedPath       = null;

        if (best.isPresent()) {
            ImageInfo winner = best.get();
            selectedFilename = winner.getFilename();

            long rank = scoreable.stream()
                    .filter(i -> i.getScore() != null)
                    .filter(i -> i.getScore().getTotalScore() > winner.getScore().getTotalScore())
                    .count();

            String selectedReason = String.format(
                    "Best total score %.2f among %d scored candidate(s) (rank #%d)",
                    winner.getScore().getTotalScore(), scoreable.size(), rank + 1);

            winner.getScore().setSelected(true);
            winner.getScore().setSelectionReason(selectedReason);

            Path src = Path.of(winner.getLocalPath());
            selectedPath = selectedDir.resolve(winner.getFilename());
            try {
                Files.copy(src, selectedPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("Could not copy selected image {}: {}", winner.getFilename(), e.getMessage());
            }
        }

        return ProcessingResult.builder()
                .upc(upc)
                .selectedFilename(selectedFilename)
                .selectedImagePath(selectedPath != null ? selectedPath.toAbsolutePath().toString() : null)
                .manualOverride(false)
                .processedAt(LocalDateTime.now())
                .candidates(candidates)
                .build();
    }

    private void step(ProcessingStatus s, String msg) {
        s.setCurrentStep(msg);
        log.info(msg);
    }
}
