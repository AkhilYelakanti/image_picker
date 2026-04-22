package com.upc.imageselector.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.dto.OverrideRequestDto;
import com.upc.imageselector.exception.ResourceNotFoundException;
import com.upc.imageselector.model.*;
import com.upc.imageselector.service.PersistenceService;
import com.upc.imageselector.service.ProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProcessingService processingService;
    @MockBean PersistenceService persistenceService;
    @MockBean AppProperties appProperties;

    @Test
    void postProcess_returnsAccepted() throws Exception {
        doNothing().when(processingService).startProcessing();

        mvc.perform(post("/api/process"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void postProcess_returnsConflictIfAlreadyRunning() throws Exception {
        doThrow(new IllegalStateException("already running"))
                .when(processingService).startProcessing();

        mvc.perform(post("/api/process"))
                .andExpect(status().isConflict());
    }

    @Test
    void getStatus_returnsCurrentStatus() throws Exception {
        ProcessingStatus s = new ProcessingStatus();
        s.setState(ProcessingStatus.State.IDLE);
        when(processingService.getStatus()).thenReturn(s);

        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void getResults_returnsAllUpcs() throws Exception {
        ProcessingResult r = ProcessingResult.builder()
                .upc("00012345678901")
                .selectedFilename("00012345678901_1.jpg")
                .processedAt(LocalDateTime.now())
                .candidates(List.of())
                .build();
        when(persistenceService.getAll()).thenReturn(Map.of("00012345678901", r));

        mvc.perform(get("/api/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].upc").value("00012345678901"))
                .andExpect(jsonPath("$[0].selectedFilename").value("00012345678901_1.jpg"));
    }

    @Test
    void getResultByUpc_returnsCorrectResult() throws Exception {
        ProcessingResult r = ProcessingResult.builder()
                .upc("00012345678901")
                .selectedFilename("00012345678901_1.jpg")
                .processedAt(LocalDateTime.now())
                .candidates(List.of())
                .build();
        when(persistenceService.getByUpc("00012345678901")).thenReturn(Optional.of(r));

        mvc.perform(get("/api/results/00012345678901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upc").value("00012345678901"));
    }

    @Test
    void getResultByUpc_returns404WhenNotFound() throws Exception {
        when(persistenceService.getByUpc(anyString()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mvc.perform(get("/api/results/00000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postOverride_returnsUpdatedResult() throws Exception {
        ProcessingResult updated = ProcessingResult.builder()
                .upc("00012345678901")
                .selectedFilename("00012345678901_70.jpg")
                .manualOverride(true)
                .processedAt(LocalDateTime.now())
                .candidates(List.of())
                .build();
        when(processingService.overrideSelection("00012345678901", "00012345678901_70.jpg"))
                .thenReturn(updated);

        OverrideRequestDto body = new OverrideRequestDto();
        body.setFilename("00012345678901_70.jpg");

        mvc.perform(post("/api/results/00012345678901/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedFilename").value("00012345678901_70.jpg"))
                .andExpect(jsonPath("$.manualOverride").value(true));
    }

    @Test
    void postOverride_returns400WhenFilenameBlank() throws Exception {
        mvc.perform(post("/api/results/00012345678901/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
