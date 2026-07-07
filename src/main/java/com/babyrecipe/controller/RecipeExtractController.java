package com.babyrecipe.controller;

import com.babyrecipe.dto.request.RecipeExtractRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.RecipeExtractResponse;
import com.babyrecipe.service.RecipeExtractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/recipes/extract")
@RequiredArgsConstructor
public class RecipeExtractController {

    private final RecipeExtractService recipeExtractService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<RecipeExtractResponse>>> extract(
        @Valid @RequestBody RecipeExtractRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recipeExtractService.extract(request.getUrl())));
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<RecipeExtractResponse>>> extractFromImages(
        @RequestPart("images") List<MultipartFile> images
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recipeExtractService.extractFromImages(images)));
    }
}
