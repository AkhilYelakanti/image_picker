package com.upc.imageselector.dto;

import com.upc.imageselector.model.ProcessingStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessingStatusDto {

    private String state;
    private int totalUrls;
    private int downloadedCount;
    private int failedDownloads;
    private int totalUpcs;
    private int scoredUpcs;
    private int progressPercent;
    private String currentStep;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static ProcessingStatusDto from(ProcessingStatus s) {
        ProcessingStatusDto dto = new ProcessingStatusDto();
        dto.state = s.getState().name();
        dto.totalUrls = s.getTotalUrls();
        dto.downloadedCount = s.getDownloadedCount();
        dto.failedDownloads = s.getFailedDownloads();
        dto.totalUpcs = s.getTotalUpcs();
        dto.scoredUpcs = s.getScoredUpcs();
        dto.progressPercent = s.progressPercent();
        dto.currentStep = s.getCurrentStep();
        dto.errorMessage = s.getErrorMessage();
        dto.startedAt = s.getStartedAt();
        dto.completedAt = s.getCompletedAt();
        return dto;
    }
}
