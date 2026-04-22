package com.upc.imageselector.controller;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.model.ProcessingResult;
import com.upc.imageselector.service.PersistenceService;
import com.upc.imageselector.service.ProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class UiController {

    private final ProcessingService processingService;
    private final PersistenceService persistenceService;
    private final AppProperties appProperties;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("status", processingService.getStatus());
        Map<String, ProcessingResult> all = persistenceService.getAll();
        model.addAttribute("totalUpcs", all.size());
        model.addAttribute("manualOverrides",
                all.values().stream().filter(ProcessingResult::isManualOverride).count());
        model.addAttribute("hasResults", !all.isEmpty());
        String linkFile = appProperties.getImagesLinkFile();
        model.addAttribute("linkFilePath", linkFile);
        model.addAttribute("linkFileExists", Files.exists(Path.of(linkFile).toAbsolutePath()));
        return "upc-image-selector/index";
    }

    @GetMapping("/review")
    public String review(@RequestParam(required = false) String upc, Model model) {
        Map<String, ProcessingResult> all = persistenceService.getAll();

        if (upc != null && !upc.isBlank()) {
            // Single-UPC view
            ProcessingResult single = all.get(upc);
            model.addAttribute("results",
                    single != null ? List.of(single) : List.of());
            model.addAttribute("filterUpc", upc);
        } else {
            // All UPCs sorted
            List<ProcessingResult> sorted = all.values().stream()
                    .sorted(Comparator.comparing(ProcessingResult::getUpc))
                    .toList();
            model.addAttribute("results", sorted);
            model.addAttribute("filterUpc", "");
        }

        model.addAttribute("totalCount", all.size());
        model.addAttribute("status", processingService.getStatus());
        return "upc-image-selector/review";
    }

    @GetMapping("/review/{upc}")
    public String reviewSingle(@PathVariable String upc, Model model) {
        return review(upc, model);
    }
}
