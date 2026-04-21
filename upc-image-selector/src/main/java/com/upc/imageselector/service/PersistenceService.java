package com.upc.imageselector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.model.ProcessingResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PersistenceService {

    private static final String RESULTS_FILE = "processing_results.json";

    private final AppProperties props;
    private final ObjectMapper objectMapper;

    private final Map<String, ProcessingResult> cache = new LinkedHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(resultsDir());
        Files.createDirectories(Path.of(props.getDownloadDir()));
        Files.createDirectories(Path.of(props.getSelectedDir()));
        loadFromDisk();
    }

    public synchronized void saveAll(Map<String, ProcessingResult> results) {
        cache.clear();
        cache.putAll(results);
        persist();
    }

    public synchronized void saveOne(ProcessingResult result) {
        cache.put(result.getUpc(), result);
        persist();
    }

    public synchronized Map<String, ProcessingResult> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(cache));
    }

    public synchronized Optional<ProcessingResult> getByUpc(String upc) {
        return Optional.ofNullable(cache.get(upc));
    }

    public synchronized boolean isEmpty() {
        return cache.isEmpty();
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void loadFromDisk() {
        Path file = resultsDir().resolve(RESULTS_FILE);
        if (!Files.exists(file)) return;
        try {
            ResultsWrapper wrapper = objectMapper.readValue(file.toFile(), ResultsWrapper.class);
            if (wrapper.results != null) {
                cache.putAll(wrapper.results);
                log.info("Loaded {} UPC results from disk", cache.size());
            }
        } catch (IOException e) {
            log.warn("Could not load results from disk: {}", e.getMessage());
        }
    }

    private void persist() {
        Path file = resultsDir().resolve(RESULTS_FILE);
        try {
            ResultsWrapper wrapper = new ResultsWrapper(new LinkedHashMap<>(cache));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), wrapper);
        } catch (IOException e) {
            log.error("Failed to persist results: {}", e.getMessage());
        }
    }

    private Path resultsDir() {
        return Path.of(props.getResultsDir());
    }

    // ── wrapper for JSON root ─────────────────────────────────────────────────

    static class ResultsWrapper {
        public Map<String, ProcessingResult> results;
        ResultsWrapper() {}
        ResultsWrapper(Map<String, ProcessingResult> results) { this.results = results; }
    }
}
