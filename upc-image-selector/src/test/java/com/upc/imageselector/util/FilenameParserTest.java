package com.upc.imageselector.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameParserTest {

    private final FilenameParser parser = new FilenameParser();

    @Test
    void parsesBareFrontFilename() {
        Optional<FilenameParser.ParsedFilename> result =
                parser.parse("00012345678901_1.jpg");
        assertThat(result).isPresent();
        assertThat(result.get().upc()).isEqualTo("00012345678901");
        assertThat(result.get().imageType()).isEqualTo("1");
        assertThat(result.get().extension()).isEqualToIgnoringCase("jpg");
    }

    @Test
    void parsesFilenameEmbeddedInUrl() {
        Optional<FilenameParser.ParsedFilename> result =
                parser.parse("https://cdn.example.com/images/00099482449362_70.jpg");
        assertThat(result).isPresent();
        assertThat(result.get().upc()).isEqualTo("00099482449362");
        assertThat(result.get().imageType()).isEqualTo("70");
    }

    @Test
    void parsesAlphanumericImageType() {
        Optional<FilenameParser.ParsedFilename> result =
                parser.parse("00012345678901_74.jpeg");
        assertThat(result).isPresent();
        assertThat(result.get().imageType()).isEqualTo("74");
    }

    @Test
    void returnsEmptyForTooShortUpc() {
        Optional<FilenameParser.ParsedFilename> result =
                parser.parse("0001234_1.jpg");   // only 7 digits
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void returnsEmptyForUrlWithNoMatchingFilename() {
        assertThat(parser.parse("https://example.com/no-upc-here.jpg")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "00012345678901_1.jpg,     00012345678901, 1",
            "00012345678901_70.jpg,    00012345678901, 70",
            "00012345678901_74.jpg,    00012345678901, 74",
            "00012345678901_21.jpg,    00012345678901, 21",
            "00099482449362_100A.jpg,  00099482449362, 100A"
    })
    void parsesVariousTypes(String input, String expectedUpc, String expectedType) {
        Optional<FilenameParser.ParsedFilename> result = parser.parse(input);
        assertThat(result).isPresent();
        assertThat(result.get().upc()).isEqualTo(expectedUpc);
        assertThat(result.get().imageType()).isEqualTo(expectedType);
    }

    @Test
    void canonicalFilenameIsLowercase() {
        FilenameParser.ParsedFilename pf = parser.parse("00012345678901_1.JPG").get();
        assertThat(pf.canonicalFilename()).isEqualTo("00012345678901_1.jpg");
    }
}
