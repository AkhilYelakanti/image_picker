package com.upc.imageselector.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingResult {

    private String upc;
    private String selectedFilename;
    private String selectedImagePath;
    private boolean manualOverride;
    private LocalDateTime processedAt;
    private LocalDateTime overriddenAt;

    /** All candidate images for this UPC (scored). */
    private List<ImageInfo> candidates;

    @JsonIgnore
    public ImageInfo getSelectedImage() {
        if (candidates == null || selectedFilename == null) return null;
        return candidates.stream()
                .filter(i -> selectedFilename.equals(i.getFilename()))
                .findFirst()
                .orElse(null);
    }

    @JsonIgnore
    public boolean hasValidSelection() {
        return selectedFilename != null && !selectedFilename.isBlank();
    }
}
