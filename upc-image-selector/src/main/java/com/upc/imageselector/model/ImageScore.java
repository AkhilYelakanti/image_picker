package com.upc.imageselector.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageScore {

    // Component scores (sum = total)
    private double resolutionScore;    // 0-20
    private double sharpnessScore;     // 0-25
    private double brightnessScore;    // 0-15
    private double contrastScore;      // 0-15
    private double backgroundScore;    // 0-15
    private double centeringScore;     // 0-10
    private double aspectRatioScore;    // 0-15
    private double labelPresenceScore;  // 0-20
    private double foregroundShapeScore; // 0-12
    private double typeTiebreaker;      // 0-3

    private double totalScore;          // 0-150

    // Raw metrics (for debugging / display)
    private double laplacianVariance;
    private double meanBrightness;
    private double stdDevBrightness;
    private double borderSaturation;   // 0-255 scale
    private double borderBrightness;   // 0-255 scale

    private String selectionReason;
    private boolean selected;

    public static ImageScore failed(String reason) {
        return ImageScore.builder()
                .totalScore(0)
                .selectionReason("Scoring failed: " + reason)
                .build();
    }
}
