package com.upc.imageselector.dto;

import com.upc.imageselector.model.ImageScore;
import lombok.Data;

@Data
public class ImageScoreDto {

    private double totalScore;
    private double resolutionScore;
    private double sharpnessScore;
    private double brightnessScore;
    private double contrastScore;
    private double backgroundScore;
    private double centeringScore;
    private double typeTiebreaker;

    private double laplacianVariance;
    private double meanBrightness;
    private double stdDevBrightness;
    private double borderSaturation;
    private double borderBrightness;

    private String selectionReason;
    private boolean selected;

    public static ImageScoreDto from(ImageScore s) {
        if (s == null) return null;
        ImageScoreDto dto = new ImageScoreDto();
        dto.totalScore = s.getTotalScore();
        dto.resolutionScore = s.getResolutionScore();
        dto.sharpnessScore = s.getSharpnessScore();
        dto.brightnessScore = s.getBrightnessScore();
        dto.contrastScore = s.getContrastScore();
        dto.backgroundScore = s.getBackgroundScore();
        dto.centeringScore = s.getCenteringScore();
        dto.typeTiebreaker = s.getTypeTiebreaker();
        dto.laplacianVariance = s.getLaplacianVariance();
        dto.meanBrightness = s.getMeanBrightness();
        dto.stdDevBrightness = s.getStdDevBrightness();
        dto.borderSaturation = s.getBorderSaturation();
        dto.borderBrightness = s.getBorderBrightness();
        dto.selectionReason = s.getSelectionReason();
        dto.selected = s.isSelected();
        return dto;
    }
}
