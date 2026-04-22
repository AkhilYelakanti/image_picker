package com.upc.imageselector.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.dto.ImageLinksRequestDto;
import com.upc.imageselector.dto.LinksProcessingResultDto;
import com.upc.imageselector.service.PersistenceService;
import com.upc.imageselector.service.ProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
class LinksProcessingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProcessingService processingService;
    @MockBean PersistenceService persistenceService;
    @MockBean AppProperties appProperties;

    @Test
    void postProcessLinks_returnsResultDto() throws Exception {
        LinksProcessingResultDto dto = LinksProcessingResultDto.builder()
                .totalLinks(2)
                .validLinks(2)
                .invalidLinks(0)
                .downloadedCount(1)
                .failedDownloads(1)
                .totalGroups(1)
                .failedUrls(List.of("https://cdn.example.com/00012345678901_70.jpg"))
                .groups(Map.of())
                .processedAt(LocalDateTime.now())
                .build();

        when(processingService.processLinks(anyList())).thenReturn(dto);

        ImageLinksRequestDto body = new ImageLinksRequestDto();
        body.setImageLinks(List.of(
                "https://cdn.example.com/00012345678901_1.jpg",
                "https://cdn.example.com/00012345678901_70.jpg"));

        mvc.perform(post("/api/process/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLinks").value(2))
                .andExpect(jsonPath("$.downloadedCount").value(1))
                .andExpect(jsonPath("$.totalGroups").value(1))
                .andExpect(jsonPath("$.failedUrls[0]").value("https://cdn.example.com/00012345678901_70.jpg"));
    }

    @Test
    void postProcessLinks_returns400WhenLinksEmpty() throws Exception {
        ImageLinksRequestDto body = new ImageLinksRequestDto();
        body.setImageLinks(List.of());

        mvc.perform(post("/api/process/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postProcessLinks_returns400WhenBodyMissingImageLinks() throws Exception {
        mvc.perform(post("/api/process/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
