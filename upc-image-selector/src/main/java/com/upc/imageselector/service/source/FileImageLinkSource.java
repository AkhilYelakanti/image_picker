package com.upc.imageselector.service.source;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileImageLinkSource implements ImageLinkSource {

    private final Path linkFile;

    public FileImageLinkSource(Path linkFile) {
        this.linkFile = linkFile;
    }

    @Override
    public List<String> loadUrls() throws IOException {
        if (!Files.exists(linkFile)) {
            throw new FileNotFoundException("Link file not found: " + linkFile.toAbsolutePath());
        }
        return Files.readAllLines(linkFile).stream()
                .map(String::trim)
                .filter(l -> !l.isBlank() && !l.startsWith("#"))
                .toList();
    }
}
