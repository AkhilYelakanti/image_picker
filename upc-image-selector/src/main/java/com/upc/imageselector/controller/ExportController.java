package com.upc.imageselector.controller;

import com.upc.imageselector.exception.ProcessingException;
import com.upc.imageselector.service.ExportService;
import com.upc.imageselector.service.PersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final PersistenceService persistenceService;

    @GetMapping("/txt")
    public ResponseEntity<StreamingResponseBody> exportTxt() {
        StreamingResponseBody body = out -> {
            try {
                exportService.streamTxt(persistenceService.getAll(), out);
            } catch (Exception e) {
                throw new ProcessingException("TXT export failed", e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"selected_front_images.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    @GetMapping("/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv() {
        StreamingResponseBody body = out -> {
            try {
                exportService.streamCsv(persistenceService.getAll(), out);
            } catch (Exception e) {
                throw new ProcessingException("CSV export failed", e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"selected_front_images.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }
}
