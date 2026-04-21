package com.upc.imageselector.service.source;

import java.util.List;

public class ListImageLinkSource implements ImageLinkSource {

    private final List<String> urls;

    public ListImageLinkSource(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public List<String> loadUrls() {
        return urls.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
