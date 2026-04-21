package com.upc.imageselector.service;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.exception.ResourceNotFoundException;
import com.upc.imageselector.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingServiceTest {

    @Mock private AppProperties props;
    @Mock private DownloadService downloadService;
    @Mock private ScoringService scoringService;
    @Mock private PersistenceService persistenceService;
    @Mock private ExportService exportService;

    private ProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new ProcessingService(
                props, downloadService, scoringService, persistenceService, exportService);
    }

    @Test
    void initialStatusIsIdle() {
        assertThat(processingService.getStatus().getState())
                .isEqualTo(ProcessingStatus.State.IDLE);
    }

    @Test
    void isRunningReturnsFalseWhenIdle() {
        assertThat(processingService.isRunning()).isFalse();
    }

    @Test
    void overrideThrowsWhenUpcNotFound() {
        when(persistenceService.getByUpc("00000000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                processingService.overrideSelection("00000000000000", "00000000000000_1.jpg"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No result for UPC");
    }

    @Test
    void overrideThrowsWhenFilenameNotACandidate() {
        ProcessingResult result = ProcessingResult.builder()
                .upc("00012345678901")
                .selectedFilename("00012345678901_1.jpg")
                .candidates(List.of(
                        ImageInfo.builder().filename("00012345678901_1.jpg").build()
                ))
                .build();

        when(persistenceService.getByUpc("00012345678901")).thenReturn(Optional.of(result));

        assertThatThrownBy(() ->
                processingService.overrideSelection("00012345678901", "00012345678901_99.jpg"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not a candidate");
    }

    @Test
    void overrideUpdatesSelectionAndMarksManual() {
        ImageScore score1 = ImageScore.builder().totalScore(80).selected(true).build();
        ImageInfo img1 = ImageInfo.builder()
                .filename("00012345678901_1.jpg").upc("00012345678901")
                .score(score1).build();
        ImageScore score70 = ImageScore.builder().totalScore(60).selected(false).build();
        ImageInfo img70 = ImageInfo.builder()
                .filename("00012345678901_70.jpg").upc("00012345678901")
                .score(score70).build();

        ProcessingResult result = ProcessingResult.builder()
                .upc("00012345678901")
                .selectedFilename("00012345678901_1.jpg")
                .manualOverride(false)
                .candidates(List.of(img1, img70))
                .build();

        when(persistenceService.getByUpc("00012345678901")).thenReturn(Optional.of(result));
        when(props.getDownloadDir()).thenReturn("downloaded_images");
        when(props.getSelectedDir()).thenReturn("selected_front_images");
        when(persistenceService.getAll()).thenReturn(java.util.Map.of());

        ProcessingResult updated =
                processingService.overrideSelection("00012345678901", "00012345678901_70.jpg");

        assertThat(updated.getSelectedFilename()).isEqualTo("00012345678901_70.jpg");
        assertThat(updated.isManualOverride()).isTrue();
        assertThat(updated.getOverriddenAt()).isNotNull();
        verify(persistenceService).saveOne(any(ProcessingResult.class));
    }
}
