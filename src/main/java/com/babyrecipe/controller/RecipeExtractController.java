package com.babyrecipe.controller;

import com.babyrecipe.dto.request.RecipeExtractRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.RecipeExtractResponse;
import com.babyrecipe.service.RecipeExtractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipes/extract")
@RequiredArgsConstructor
public class RecipeExtractController {

    private final RecipeExtractService recipeExtractService;

    @PostMapping
    public ResponseEntity<ApiResponse<RecipeExtractResponse>> extract(
        @Valid @RequestBody RecipeExtractRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recipeExtractService.extract(request.getUrl())));
    }
}
