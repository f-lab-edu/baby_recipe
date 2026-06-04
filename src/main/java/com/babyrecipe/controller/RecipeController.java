package com.babyrecipe.controller;

import com.babyrecipe.domain.Recipe;
import com.babyrecipe.dto.request.RecipeRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.RecipeResponse;
import com.babyrecipe.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RecipeResponse>>> getRecipes(
        @RequestParam(required = false) Recipe.AgeGroup ageGroup,
        @RequestParam(required = false) Recipe.Category category,
        @RequestParam(required = false) String keyword,
        @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = userDetails != null ? Long.parseLong(userDetails.getUsername()) : null;
        return ResponseEntity.ok(ApiResponse.ok(
            recipeService.getRecipes(ageGroup, category, keyword, pageable, userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(
        @Valid @RequestBody RecipeRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(recipeService.createRecipe(request, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecipeResponse>> getRecipe(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = userDetails != null ? Long.parseLong(userDetails.getUsername()) : null;
        return ResponseEntity.ok(ApiResponse.ok(recipeService.getRecipeWithView(id, userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateRecipe(
        @PathVariable Long id,
        @Valid @RequestBody RecipeRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(recipeService.updateRecipe(id, request, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRecipe(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        recipeService.deleteRecipe(id, Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok("레시피가 삭제되었습니다."));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        boolean liked = recipeService.toggleLike(id, Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok(liked ? "좋아요 추가" : "좋아요 취소", liked));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Boolean>> toggleBookmark(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        boolean bookmarked = recipeService.toggleBookmark(id, Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok(bookmarked ? "북마크 추가" : "북마크 취소", bookmarked));
    }
}
