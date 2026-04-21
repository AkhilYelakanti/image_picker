package com.upc.imageselector.dto;

import com.upc.imageselector.model.ImageInfo;
import lombok.Data;

@Data
public class ImageInfoDto {

    private String filename;
    private String upc;
    private String imageType;
    private String url;
    private int width;
    private int height;
    private long fileSizeBytes;
    private boolean downloadFailed;
    private String downloadError;
    private ImageScoreDto score;

    public static ImageInfoDto from(ImageInfo i) {
        if (i == null) return null;
        ImageInfoDto dto = new ImageInfoDto();
        dto.filename = i.getFilename();
        dto.upc = i.getUpc();
        dto.imageType = i.getImageType();
        dto.url = i.getUrl();
        dto.width = i.getWidth();
        dto.height = i.getHeight();
        dto.fileSizeBytes = i.getFileSizeBytes();
        dto.downloadFailed = i.isDownloadFailed();
        dto.downloadError = i.getDownloadError();
        dto.score = ImageScoreDto.from(i.getScore());
        return dto;
    }
}
