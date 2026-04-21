package com.upc.imageselector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String imagesLinkFile = "ImagesLink.txt";
    private String downloadDir = "downloaded_images";
    private String selectedDir = "selected_front_images";
    private String resultsDir = "results";
    private int downloadThreads = 10;
    private int downloadTimeoutSeconds = 30;
    private int readTimeoutSeconds = 60;
    private long maxImageSizeBytes = 52_428_800L; // 50 MB

    private Scoring scoring = new Scoring();

    @Data
    public static class Scoring {
        private int workingSize = 600;
        private double borderFraction = 0.15;
        private double sharpnessScale = 500.0;
        private double idealBrightness = 150.0;
        private double idealContrastLow = 40.0;
        private double idealContrastHigh = 80.0;
    }
}
