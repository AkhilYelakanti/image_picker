package com.upc.imageselector.controller;

import com.upc.imageselector.dto.OverrideRequestDto;
import com.upc.imageselector.dto.ProcessingStatusDto;
import com.upc.imageselector.dto.UpcResultDto;
import com.upc.imageselector.model.ProcessingResult;
import com.upc.imageselector.service.PersistenceService;
import com.upc.imageselector.service.ProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ProcessingService processingService;
    private final PersistenceService persistenceService;

    /**
     * Trigger the full download → score → select pipeline.
     * Returns immediately; poll /api/status for progress.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> process() {
        processingService.startProcessing();
        return ResponseEntity.accepted()
                .body(Map.of("message", "Processing started. Poll /api/status for progress."));
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
