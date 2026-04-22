package com.upc.imageselector.service;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.model.ImageScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.within;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private ScoringService scoringService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        scoringService = new ScoringService(props);
    }

    // ── unit tests for scoring components ─────────────────────────────────────

    @Test
    void resolutionScore_fullAtTwoMegapixels() {
        // 1414x1414 ≈ 2MP; score should be very close to 20
        assertThat(scoringService.scoreResolution(1414, 1414)).isCloseTo(20.0, within(0.1));
        // Exact 2MP = exactly 20
        assertThat(scoringService.scoreResolution(2000, 1000)).isEqualTo(20.0);
    }

    @Test
    void resolutionScore_halfAtOneMegapixel() {
        double score = scoringService.scoreResolution(1000, 1000); // 1MP
        assertThat(score).isEqualTo(10.0);
    }

    @Test
    void resolutionScore_capsAtTwenty() {
        double score = scoringService.scoreResolution(4000, 4000); // 16MP
        assertThat(score).isEqualTo(20.0);
    }

    @Test
    void brightnessScore_idealGivesMaxScore() {
        double score = scoringService.scoreBrightness(150.0);
        assertThat(score).isEqualTo(15.0);
    }

    @Test
    void brightnessScore_veryDarkGivesLowScore() {
        double score = scoringService.scoreBrightness(20.0);
        assertThat(score).isLessThan(5.0);
    }

    @Test
    void brightnessScore_veryBrightGivesLowScore() {
        double score = scoringService.scoreBrightness(245.0);
        assertThat(score).isLessThan(5.0);
    }

    @Test
    void contrastScore_idealRangeGivesFullScore() {
        assertThat(scoringService.scoreContrast(60.0)).isEqualTo(15.0);
        assertThat(scoringService.scoreContrast(75.0)).isEqualTo(15.0);
    }

    @Test
    void contrastScore_flatImageGivesLowScore() {
        double score = scoringService.scoreContrast(5.0);
        assertThat(score).isLessThan(5.0);
    }

    @Test
    void typeBonus_type1GivesMax() {
        assertThat(scoringService.typeBonus("1")).isEqualTo(3.0);
    }

    @Test
    void typeBonus_unknownTypeGivesZero() {
        assertThat(scoringService.typeBonus("999")).isEqualTo(0.0);
        assertThat(scoringService.typeBonus(null)).isEqualTo(0.0);
    }

    @Test
    void typeBonus_detailTypesGiveZero() {
        assertThat(scoringService.typeBonus("74")).isEqualTo(0.0);
        assertThat(scoringService.typeBonus("21")).isEqualTo(0.0);
    }

    // ── aspect ratio ──────────────────────────────────────────────────────────

    @Test
    void aspectRatio_tallPortraitGivesMaxScore() {
        assertThat(scoringService.scoreAspectRatio(1000, 1500)).isEqualTo(15.0);
        assertThat(scoringService.scoreAspectRatio(800, 1400)).isEqualTo(15.0);
    }

    @Test
    void aspectRatio_portraitScoresHigherThanSquare() {
        double portrait = scoringService.scoreAspectRatio(1000, 1300);
        double square   = scoringService.scoreAspectRatio(1000, 1000);
        assertThat(portrait).isGreaterThan(square);
        assertThat(portrait).isGreaterThanOrEqualTo(12.0);
    }

    @Test
    void aspectRatio_squareScoresLower() {
        double square = scoringService.scoreAspectRatio(1000, 1000);
        assertThat(square).isLessThan(9.0);
    }

    @Test
    void aspectRatio_landscapeScoresLowest() {
        double landscape = scoringService.scoreAspectRatio(1500, 800);
        double square    = scoringService.scoreAspectRatio(1000, 1000);
        assertThat(landscape).isLessThan(square);
    }

    // ── label presence ────────────────────────────────────────────────────────

    @Test
    void labelPresence_multiColorBandedImageScoresHigherThanMonotone() {
        double labelScore = scoringService.scoreLabelPresence(
                createLabeledProductImage(), 200, 300);
        double capScore   = scoringService.scoreLabelPresence(
                createCapTopImage(), 200, 200);
        assertThat(labelScore).isGreaterThan(capScore);
    }

    @Test
    void labelPresence_capImageScoresLowerThanFront() {
        double capScore   = scoringService.scoreLabelPresence(createCapTopImage(), 200, 200);
        double frontScore = scoringService.scoreLabelPresence(createLabeledProductImage(), 200, 300);
        // Cap has 2 color regions (amber body + dark center); front has 5+
        assertThat(capScore).isLessThan(frontScore);
        // Combined with aspect ratio penalty for square caps, front always wins
        double capTotal   = capScore + scoringService.scoreAspectRatio(200, 200);
        double frontTotal = frontScore + scoringService.scoreAspectRatio(200, 300);
        assertThat(frontTotal).isGreaterThan(capTotal);
    }

    @Test
    void grayscaleArray_whiteImageAllMaxValues() {
        BufferedImage white = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = white.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        int[] gray = scoringService.toGrayscaleArray(white);
        for (int v : gray) {
            assertThat(v).isEqualTo(255);
        }
    }

    @Test
    void grayscaleArray_blackImageAllZero() {
        BufferedImage black = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        int[] gray = scoringService.toGrayscaleArray(black);
        for (int v : gray) {
            assertThat(v).isEqualTo(0);
        }
    }

    // ── integration-style: score a real image file ────────────────────────────

    @Test
    void score_sharpHighContrastImageScoresHigh() throws IOException {
        Path img = writeSyntheticImage(createSharpImage(), "sharp.jpg");
        ImageScore score = scoringService.score(img, "1");
        assertThat(score.getTotalScore()).isGreaterThan(40.0);
        assertThat(score.getSharpnessScore()).isGreaterThan(0.0);
    }

    @Test
    void score_blurryFlatImageScoresLow() throws IOException {
        Path img = writeSyntheticImage(createBlurryImage(), "blurry.jpg");
        ImageScore score = scoringService.score(img, "70");
        // Blurry / low-contrast image should score lower in sharpness
        assertThat(score.getSharpnessScore()).isLessThan(20.0);
    }

    @Test
    void score_missingFileReturnsFailedScore() {
        ImageScore score = scoringService.score(tempDir.resolve("nonexistent.jpg"), "1");
        assertThat(score.getTotalScore()).isEqualTo(0.0);
        assertThat(score.getSelectionReason()).contains("Scoring failed");
    }

    @Test
    void score_returnsBrightnessStats() throws IOException {
        Path img = writeSyntheticImage(createBrightImage(), "bright.jpg");
        ImageScore score = scoringService.score(img, "1");
        assertThat(score.getMeanBrightness()).isGreaterThan(180.0);
    }

    // ── image factories ───────────────────────────────────────────────────────

    private BufferedImage createSharpImage() {
        // Checkerboard pattern: maximum local contrast = sharp
        int size = 200;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFFFFFF : 0x000000);
            }
        }
        return img;
    }

    private BufferedImage createBlurryImage() {
        // Uniform grey – zero Laplacian variance
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(160, 160, 160));
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        return img;
    }

    private BufferedImage createBrightImage() {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(230, 230, 230));
        g.fillRect(0, 0, 200, 200);
        // Draw a small dark square in centre to give some contrast
        g.setColor(Color.DARK_GRAY);
        g.fillRect(80, 80, 40, 40);
        g.dispose();
        return img;
    }

    private Path writeSyntheticImage(BufferedImage img, String name) throws IOException {
        Path file = tempDir.resolve(name);
        ImageIO.write(img, "jpg", file.toFile());
        return file;
    }

    /** Simulates a front-of-pack bottle: amber neck → orange label with green/red graphics → amber body. */
    private BufferedImage createLabeledProductImage() {
        int w = 200, h = 300;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(200, 150, 50));   // amber bottle body
        g.fillRect(60, 20, 80, 260);
        g.setColor(new Color(240, 110, 20));   // orange label band
        g.fillRect(60, 90, 80, 120);
        g.setColor(new Color(50, 150, 60));    // green leaf graphic
        g.fillRect(70, 100, 30, 30);
        g.setColor(new Color(180, 60, 40));    // red fruit graphic
        g.fillRect(110, 100, 25, 25);
        g.setColor(Color.BLACK);               // brand text
        g.fillRect(70, 145, 60, 12);
        g.dispose();
        return img;
    }

    /** Simulates a cap/top view: amber circle on white with dark center — 2 color regions. */
    private BufferedImage createCapTopImage() {
        int w = 200, h = 200;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(200, 150, 50));   // amber product body
        g.fillOval(10, 10, 180, 180);
        g.setColor(new Color(25, 25, 25));     // dark cap center
        g.fillOval(55, 55, 90, 90);
        g.dispose();
        return img;
    }
}
