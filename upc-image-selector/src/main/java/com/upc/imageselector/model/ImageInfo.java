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
public class ImageInfo {

    private String filename;
    private String upc;
    private String imageType;
    private String url;
    private String localPath;

    private int width;
    private int height;
    private long fileSizeBytes;

    private boolean downloadFailed;
    private String downloadError;

    private ImageScore score;

    public long megapixels() {
        return (long) width * height;
    }

    public String displayName() {
        return filename != null ? filename : url;
    }
}
