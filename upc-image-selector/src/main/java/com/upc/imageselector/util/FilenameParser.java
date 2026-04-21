package com.upc.imageselector.util;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses filenames in the format {@code {14-digit-UPC}_{imageType}.{ext}}.
 * The pattern is matched anywhere in a string (URL or bare filename).
 */
@Component
public class FilenameParser {

    // Matches 14-digit UPC followed by _ then image type then extension
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "(\\d{14})_(\\w+)\\.(jpg|jpeg|png|gif|webp|bmp)",
            Pattern.CASE_INSENSITIVE
    );

    public record ParsedFilename(String upc, String imageType, String extension, String fullFilename) {
        public String canonicalFilename() {
            return upc + "_" + imageType + "." + extension.toLowerCase();
        }
    }

    /**
     * Extracts UPC and image-type from a URL or filename.
     *
     * @param input full URL or bare filename
     * @return parsed components, or empty if the pattern is not found
     */
    public Optional<ParsedFilename> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        Matcher m = FILE_PATTERN.matcher(input);
        if (!m.find()) return Optional.empty();
        String full = m.group(1) + "_" + m.group(2) + "." + m.group(3);
        return Optional.of(new ParsedFilename(m.group(1), m.group(2), m.group(3), full));
    }

    public boolean isValid(String input) {
        return parse(input).isPresent();
    }
}
