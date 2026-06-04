package com.babyrecipe.controller;

import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.RecipeResponse;
import com.babyrecipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RecipeResponse>>> getFeed(
        @AuthenticationPrincipal UserDetails userDetails,
        @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(recipeService.getFeed(userId, pageable)));
    }
}
