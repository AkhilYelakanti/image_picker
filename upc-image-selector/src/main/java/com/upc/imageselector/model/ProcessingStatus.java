package com.upc.imageselector.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessingStatus {

    public enum State { IDLE, RUNNING, COMPLETED, FAILED }

    private State state = State.IDLE;

    private int totalUrls;
    private int downloadedCount;
    private int failedDownloads;

    private int totalUpcs;
    private int scoredUpcs;

    private String currentStep = "";
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public int progressPercent() {
        if (state == State.IDLE) return 0;
        if (state == State.COMPLETED || state == State.FAILED) return 100;
        int total = totalUrls + totalUpcs;
        if (total == 0) return 0;
        int done = downloadedCount + failedDownloads + scoredUpcs;
        return Math.min(99, (int) (100.0 * done / total));
    }
}
