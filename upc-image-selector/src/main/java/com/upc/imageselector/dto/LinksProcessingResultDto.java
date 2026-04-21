package com.upc.imageselector.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class LinksProcessingResultDto {

    private int totalLinks;
    private int validLinks;
    private int invalidLinks;
    private int downloadedCount;
    private int failedDownloads;
    private int totalGroups;
    private List<String> failedUrls;
    private Map<String, UpcResultDto> groups;
    private LocalDateTime processedAt;
}
