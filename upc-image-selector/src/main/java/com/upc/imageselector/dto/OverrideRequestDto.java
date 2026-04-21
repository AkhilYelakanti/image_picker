package com.upc.imageselector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OverrideRequestDto {

    @NotBlank(message = "filename must not be blank")
    private String filename;
}
