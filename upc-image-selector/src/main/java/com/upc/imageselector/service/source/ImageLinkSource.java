package com.upc.imageselector.service.source;

import java.io.IOException;
import java.util.List;

/**
 * Strategy for supplying image URLs to the processing pipeline.
 */
public interface ImageLinkSource {
    List<String> loadUrls() throws IOException;
}
