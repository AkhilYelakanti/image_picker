package com.upc.imageselector.dto;

import com.upc.imageselector.model.ProcessingResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpcResultDto {

    private String upc;
    private String selectedFilename;
    private boolean manualOverride;
    private LocalDateTime processedAt;
    private LocalDateTime overriddenAt;
    private List<ImageInfoDto> candidates;
    private int candidateCount;
    private ImageInfoDto selectedImage;

    public static UpcResultDto from(ProcessingResult r) {
        if (r == null) return null;
        UpcResultDto dto = new UpcResultDto();
        dto.upc = r.getUpc();
        dto.selectedFilename = r.getSelectedFilename();
        dto.manualOverride = r.isManualOverride();
        dto.processedAt = r.getProcessedAt();
        dto.overriddenAt = r.getOverriddenAt();
        dto.candidates = r.getCandidates() == null ? List.of() :
                r.getCandidates().stream().map(ImageInfoDto::from).toList();
        dto.candidateCount = dto.candidates.size();
        dto.selectedImage = ImageInfoDto.from(r.getSelectedImage());
        return dto;
    }
}
