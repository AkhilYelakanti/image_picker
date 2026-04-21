package com.upc.imageselector.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ImageLinksRequestDto {

    @NotEmpty(message = "imageLinks must not be empty")
    private List<String> imageLinks;
}
