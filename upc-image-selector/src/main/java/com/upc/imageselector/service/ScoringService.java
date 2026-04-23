package com.upc.imageselector.service;

import com.upc.imageselector.config.AppProperties;
import com.upc.imageselector.model.ImageScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores a product image using pure-Java heuristics that proxy for
 * "good front product photography":
 *
 *  Component              Max pts  Rationale
 *  ─────────────────────  ───────  ──────────────────────────────────────
 *  Resolution             20       More pixels → more detail
 *  Sharpness (Laplacian)  25       In-focus subject is the top signal
 *  Brightness balance     15       Proper exposure (not dark / blown out)
 *  Contrast               15       Product clearly separated from bg
 *  Background plainness   15       White / light solid background
 *  Subject centering      10       Product in the middle of the frame
 *  Aspect ratio           15       Portrait (tall) = front-facing shot
 *  Label presence         20       Hue diversity + transitions = label visible
 *  Foreground shape       12       Non-white bounding-box portrait → front shot
 *  Image-type tie-break    3       type 1 preferred, weak signal only
 *  ─────────────────────  ───────
 *  Total                  150
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScoringService {

    private final AppProperties props;

    // ── public API ────────────────────────────────────────────────────────────

    public ImageScore score(Path imagePath, String imageType) {
        try {
            BufferedImage original = ImageIO.read(imagePath.toFile());
            if (original == null) {
                return ImageScore.failed("ImageIO returned null – unsupported format or corrupted file");
            }

            int origW = original.getWidth();
            int origH = original.getHeight();

            // Downsample once; all heuristics run on this working copy.
            // Resolution score still uses original dimensions.
            BufferedImage working = downsample(original, props.getScoring().getWorkingSize());
            int w = working.getWidth();
            int h = working.getHeight();

            int[] gray = toGrayscaleArray(working);

            double resScore   = scoreResolution(origW, origH);
            double[] sharpRes = scoreSharpness(gray, w, h);
            double sharpScore = sharpRes[0];
            double lapVar     = sharpRes[1];

            double[] bStats   = brightnessStats(gray);
            double mean       = bStats[0];
            double stdDev     = bStats[1];

            double brightScore = scoreBrightness(mean);
            double contScore   = scoreContrast(stdDev);

            double[] bgRes    = scoreBackground(working, w, h);
            double bgScore    = bgRes[0];
            double borderSat  = bgRes[1];
            double borderBrt  = bgRes[2];

            double centerScore = scoreCentering(gray, w, h);
            double aspectScore = scoreAspectRatio(origW, origH);
            double labelScore  = scoreLabelPresence(working, w, h);
            double fgShapeScore = scoreForegroundShape(gray, w, h);
            double typeScore   = typeBonus(imageType);

            double total = resScore + sharpScore + brightScore + contScore
                         + bgScore + centerScore + aspectScore + labelScore + fgShapeScore + typeScore;

            return ImageScore.builder()
                    .resolutionScore(r2(resScore))
                    .sharpnessScore(r2(sharpScore))
                    .brightnessScore(r2(brightScore))
                    .contrastScore(r2(contScore))
                    .backgroundScore(r2(bgScore))
                    .centeringScore(r2(centerScore))
                    .aspectRatioScore(r2(aspectScore))
                    .labelPresenceScore(r2(labelScore))
                    .foregroundShapeScore(r2(fgShapeScore))
                    .typeTiebreaker(r2(typeScore))
                    .totalScore(r2(total))
                    .laplacianVariance(r2(lapVar))
                    .meanBrightness(r2(mean))
                    .stdDevBrightness(r2(stdDev))
                    .borderSaturation(r2(borderSat))
                    .borderBrightness(r2(borderBrt))
                    .build();

        } catch (Exception ex) {
            log.warn("Scoring failed for {}: {}", imagePath, ex.getMessage());
            return ImageScore.failed(ex.getMessage());
        }
    }

    // ── scoring components ────────────────────────────────────────────────────

    /**
     * Resolution: 2 MP → full 20 pts, linear below.
     */
    double scoreResolution(int w, int h) {
        double mp = (double) w * h / 1_000_000.0;
        return Math.min(mp / 2.0, 1.0) * 20.0;
    }

    /**
     * Sharpness via Laplacian variance (sampled at stride for speed).
     * Variance ≥ 500 → full 25 pts.
     */
    double[] scoreSharpness(int[] gray, int w, int h) {
        int stride = Math.max(1, Math.min(w, h) / 200);
        List<Double> responses = new ArrayList<>((w / stride) * (h / stride));

        for (int y = 1; y < h - 1; y += stride) {
            for (int x = 1; x < w - 1; x += stride) {
                double lap = -4.0 * gray[y * w + x]
                        + gray[(y - 1) * w + x]
                        + gray[(y + 1) * w + x]
                        + gray[y * w + (x - 1)]
                        + gray[y * w + (x + 1)];
                responses.add(lap);
            }
        }

        double mean = responses.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = responses.stream()
                .mapToDouble(d -> (d - mean) * (d - mean))
                .average().orElse(0);

        double scale = props.getScoring().getSharpnessScale();
        double score = 25.0 * Math.min(variance / scale, 1.0);
        return new double[]{score, variance};
    }

    /**
     * Brightness: ideal mean ≈ 150/255.
     * Penalty grows linearly as mean deviates from ideal.
     */
    double scoreBrightness(double mean) {
        double ideal = props.getScoring().getIdealBrightness();
        double tolerance = 120.0; // ±120 from 150 covers 30-255
        double deviation = Math.abs(mean - ideal) / tolerance;
        return 15.0 * Math.max(0, 1.0 - deviation);
    }

    /**
     * Contrast (std-dev of brightness).
     * Ramp up to 60, plateau 60-80, decay above 80.
     */
    double scoreContrast(double stdDev) {
        double lo = props.getScoring().getIdealContrastLow();
        double hi = props.getScoring().getIdealContrastHigh();
        if (stdDev <= lo) return 15.0 * (stdDev / lo);
        if (stdDev <= hi) return 15.0;
        return 15.0 * Math.max(0, 1.0 - (stdDev - hi) / 100.0);
    }

    /**
     * Background score based on border region pixels.
     * Low HSB saturation + high HSB brightness = white/plain background.
     */
    double[] scoreBackground(BufferedImage img, int w, int h) {
        double frac = props.getScoring().getBorderFraction();
        int bx = Math.max(1, (int) (w * frac));
        int by = Math.max(1, (int) (h * frac));

        int stride = Math.max(1, Math.min(bx, by) / 30);
        double sumSat = 0, sumBrt = 0;
        int count = 0;

        for (int y = 0; y < h; y += stride) {
            for (int x = 0; x < w; x += stride) {
                boolean inBorder = x < bx || x >= w - bx || y < by || y >= h - by;
                if (!inBorder) continue;
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                sumSat += hsb[1];
                sumBrt += hsb[2];
                count++;
            }
        }

        if (count == 0) return new double[]{0, 128, 128};

        double meanSat = sumSat / count;   // 0-1
        double meanBrt = sumBrt / count;   // 0-1

        // Low saturation = plain background; high brightness = white background.
        double score = ((1.0 - meanSat) * 0.5 + meanBrt * 0.5) * 15.0;
        return new double[]{score, meanSat * 255, meanBrt * 255};
    }

    /**
     * Subject centering: foreground (dark pixels against assumed bright BG)
     * center-of-mass proximity to image center.
     */
    double scoreCentering(int[] gray, int w, int h) {
        // Estimate background brightness from corners
        int cs = Math.max(1, Math.min(w, h) / 10);
        double cornerSum = 0;
        int cornerCount = 0;
        for (int y = 0; y < cs; y++) {
            for (int x = 0; x < cs; x++) {
                cornerSum += gray[y * w + x];
                cornerSum += gray[y * w + (w - 1 - x)];
                cornerSum += gray[(h - 1 - y) * w + x];
                cornerSum += gray[(h - 1 - y) * w + (w - 1 - x)];
                cornerCount += 4;
            }
        }
        double bgEst = cornerCount > 0 ? cornerSum / cornerCount : 200.0;
        double threshold = bgEst - 40.0; // pixels significantly darker than background

        // Center of mass of "foreground" pixels
        int stride = Math.max(1, Math.min(w, h) / 200);
        double sumX = 0, sumY = 0, count = 0;
        for (int y = 0; y < h; y += stride) {
            for (int x = 0; x < w; x += stride) {
                if (gray[y * w + x] < threshold) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }

        if (count < 10) {
            // Sparse foreground – could be a very bright product; give neutral score
            return 5.0;
        }

        double cmX = sumX / count;
        double cmY = sumY / count;
        double cx = w / 2.0;
        double cy = h / 2.0;
        double maxDist = Math.sqrt(cx * cx + cy * cy);
        double dist = Math.sqrt((cmX - cx) * (cmX - cx) + (cmY - cy) * (cmY - cy));

        return 10.0 * (1.0 - Math.min(dist / maxDist, 1.0));
    }

    /**
     * Aspect ratio score: portrait (tall) images are front-facing product shots.
     * Square and landscape images are typically top or bottom views (cap, base).
     * Max 15 pts.
     */
    double scoreAspectRatio(int origW, int origH) {
        double ratio = (double) origH / origW;
        if (ratio >= 1.5) return 15.0;
        if (ratio >= 1.2) return 12.0 + 3.0 * ((ratio - 1.2) / 0.3);
        if (ratio >= 1.0) return 7.0  + 5.0 * ((ratio - 1.0) / 0.2);
        return Math.max(0.0, 7.0 * ratio);
    }

    /**
     * Label presence score: detects product label via color diversity and
     * transitions along vertical sample lines through the image center.
     *
     * Front-of-pack images show multiple distinct hues (brand color, graphics,
     * text). Cap / base images show only 1–2 color regions (product body +
     * dark center). Max 20 pts.
     */
    double scoreLabelPresence(BufferedImage img, int w, int h) {
        int[] xs = {w * 3 / 8, w * 7 / 16, w / 2, w * 9 / 16, w * 5 / 8};
        int stride = Math.max(1, h / 300);

        double totalTransitions = 0;
        double totalHueBuckets  = 0;

        for (int xi : xs) {
            int x = Math.max(0, Math.min(xi, w - 1));
            Set<Integer> hueBuckets = new HashSet<>();
            int prevBucket = -1;
            int transitions = 0;

            for (int y = 0; y < h; y += stride) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Skip near-white background pixels
                if (r > 230 && g > 230 && b > 230) {
                    prevBucket = -1;
                    continue;
                }

                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                // Achromatic (black/gray) → bucket 16; else quantise hue to 16 divisions
                int bucket = (hsb[1] < 0.12f) ? 16 : (int) (hsb[0] * 16);

                hueBuckets.add(bucket);
                if (prevBucket >= 0 && bucket != prevBucket) {
                    transitions++;
                }
                prevBucket = bucket;
            }

            totalTransitions += transitions;
            totalHueBuckets  += hueBuckets.size();
        }

        double avgTransitions = totalTransitions / xs.length;
        double avgHueBuckets  = totalHueBuckets  / xs.length;

        // 3+ colour transitions and 3+ distinct hue groups → full score
        double transitionScore = Math.min(avgTransitions / 3.0, 1.0);
        double diversityScore  = Math.min(avgHueBuckets  / 3.0, 1.0);

        return (transitionScore * 0.5 + diversityScore * 0.5) * 20.0;
    }

    /**
     * Foreground shape: finds the bounding box of all non-background pixels on the
     * working-size image and scores its aspect ratio using the same portrait-preference
     * curve as scoreAspectRatio.
     *
     * A circular cap viewed from above produces a square bounding box → low score.
     * A front-facing bottle produces a tall, narrow bounding box → high score.
     * Max 12 pts.
     */
    double scoreForegroundShape(int[] gray, int w, int h) {
        int minX = w, maxX = 0, minY = h, maxY = 0;
        int stride = Math.max(1, Math.min(w, h) / 150);
        boolean found = false;

        for (int y = 0; y < h; y += stride) {
            for (int x = 0; x < w; x += stride) {
                if (gray[y * w + x] < 215) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                }
            }
        }

        if (!found || maxX <= minX) return 3.0;

        int fgW = maxX - minX + 1;
        int fgH = maxY - minY + 1;
        double ratio = (double) fgH / fgW;

        if (ratio >= 1.5) return 12.0;
        if (ratio >= 1.2) return 9.0 + 3.0 * ((ratio - 1.2) / 0.3);
        if (ratio >= 1.0) return 3.0 + 6.0 * ((ratio - 1.0) / 0.2);
        return Math.max(0.0, 3.0 * ratio);
    }

    /**
     * Weak image-type bonus.
     * Type 1 is conventionally the primary front-of-pack shot.
     * Types 74/21 (close-up/detail angles) receive no bonus.
     */
    double typeBonus(String imageType) {
        if (imageType == null) return 0.0;
        return switch (imageType.trim()) {
            case "1"  -> 3.0;
            case "70" -> 2.5;
            default   -> 0.0;
        };
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BufferedImage downsample(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxSide && h <= maxSide) return src;
        double scale = (double) maxSide / Math.max(w, h);
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        return progressiveScale(src, nw, nh);
    }

    /**
     * Progressive bilinear downsampling: halves dimensions repeatedly until within
     * 2× of the target, then does one final precise scale. This avoids the aliasing
     * artifacts that occur when jumping from a large image directly to a small one in
     * a single step — the same strategy thumbnailator uses internally.
     */
    private static BufferedImage progressiveScale(BufferedImage src, int targetW, int targetH) {
        int curW = src.getWidth();
        int curH = src.getHeight();
        BufferedImage cur = src;
        while (curW > targetW * 2 || curH > targetH * 2) {
            curW = Math.max(curW / 2, targetW);
            curH = Math.max(curH / 2, targetH);
            cur  = bilinearScale(cur, curW, curH);
        }
        return (curW == targetW && curH == targetH) ? cur : bilinearScale(cur, targetW, targetH);
    }

    private static BufferedImage bilinearScale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    int[] toGrayscaleArray(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] gray = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // ITU-R BT.601 luminance
                gray[y * w + x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gray;
    }

    double[] brightnessStats(int[] gray) {
        double sum = 0;
        for (int v : gray) sum += v;
        double mean = sum / gray.length;

        double varSum = 0;
        for (int v : gray) varSum += (v - mean) * (v - mean);
        double stdDev = Math.sqrt(varSum / gray.length);
        return new double[]{mean, stdDev};
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
