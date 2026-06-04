package com.babyrecipe.controller;

import com.babyrecipe.dto.request.CommentRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.CommentResponse;
import com.babyrecipe.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipes/{recipeId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getComments(
        @PathVariable Long recipeId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getComments(recipeId, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
        @PathVariable Long recipeId,
        @Valid @RequestBody CommentRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(commentService.addComment(recipeId, request, userId)));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
        @PathVariable Long recipeId,
        @PathVariable Long commentId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        commentService.deleteComment(recipeId, commentId, Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok("댓글이 삭제되었습니다."));
    }
}
