package com.upc.imageselector.controller;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.dto.ImageLinksRequestDto;
import com.upc.imageselector.dto.LinksProcessingResultDto;
import com.upc.imageselector.dto.OverrideRequestDto;
import com.upc.imageselector.dto.ProcessingStatusDto;
import com.upc.imageselector.dto.UpcResultDto;
import com.upc.imageselector.model.ProcessingResult;
import com.upc.imageselector.service.PersistenceService;
import com.upc.imageselector.service.ProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ProcessingService processingService;
    private final PersistenceService persistenceService;
    private final AppProperties props;

    /**
     * Trigger the full download → score → select pipeline from the configured link file.
     * Returns immediately; poll /api/status for progress.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> process() {
        processingService.startProcessing();
        return ResponseEntity.accepted()
                .body(Map.of("message", "Processing started. Poll /api/status for progress."));
    }

    /**
     * Upload a new ImagesLink.txt (or any filename) and immediately start processing.
     * The file is saved to the path configured by {@code app.images-link-file}.
     * Returns 202 immediately; poll /api/status for progress.
     */
    @PostMapping(value = "/process/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> processUpload(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        Path target = Path.of(props.getImagesLinkFile()).toAbsolutePath();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        file.transferTo(target);
        processingService.startProcessing();
        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "File uploaded and processing started. Poll /api/status for progress.",
                        "savedTo", target.toString()
                ));
    }

    /**
     * Synchronously process a caller-supplied list of image URLs.
     * Returns the full result inline once complete.
     */
    @PostMapping("/process/links")
    public LinksProcessingResultDto processLinks(
            @Valid @RequestBody ImageLinksRequestDto body) throws IOException {
        return processingService.processLinks(body.getImageLinks());
    }

    /** Current processing status / progress. */
    @GetMapping("/status")
    public ProcessingStatusDto status() {
        return ProcessingStatusDto.from(processingService.getStatus());
    }

    /** All UPC results. */
    @GetMapping("/results")
    public List<UpcResultDto> results() {
        return persistenceService.getAll().values().stream()
                .map(UpcResultDto::from)
                .toList();
    }

    /** Single UPC result. */
    @GetMapping("/results/{upc}")
    public UpcResultDto resultByUpc(@PathVariable String upc) {
        ProcessingResult result = persistenceService.getByUpc(upc)
                .orElseThrow(() -> new com.upc.imageselector.exception.ResourceNotFoundException(
                        "No result found for UPC: " + upc));
        return UpcResultDto.from(result);
    }

    /**
     * Manually override the selected image for a UPC.
     * Body: {@code { "filename": "00012345678901_70.jpg" }}
     */
    @PostMapping("/results/{upc}/override")
    public UpcResultDto override(@PathVariable String upc,
                                  @Valid @RequestBody OverrideRequestDto body) {
        return UpcResultDto.from(processingService.overrideSelection(upc, body.getFilename()));
    }
}
