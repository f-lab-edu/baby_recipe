package com.babyrecipe.controller;

import com.babyrecipe.dto.request.UpdateProfileRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.RecipeResponse;
import com.babyrecipe.dto.response.UserResponse;
import com.babyrecipe.service.RecipeService;
import com.babyrecipe.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> searchUsers(
        @RequestParam String keyword,
        @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long currentUserId = userDetails != null ? Long.parseLong(userDetails.getUsername()) : null;
        return ResponseEntity.ok(ApiResponse.ok(userService.searchUsers(keyword, currentUserId, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long currentUserId = userDetails != null ? Long.parseLong(userDetails.getUsername()) : null;
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser(id, currentUserId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request)));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        userService.follow(Long.parseLong(userDetails.getUsername()), id);
        return ResponseEntity.ok(ApiResponse.ok("팔로우 완료"));
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        userService.unfollow(Long.parseLong(userDetails.getUsername()), id);
        return ResponseEntity.ok(ApiResponse.ok("언팔로우 완료"));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getFollowers(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getFollowers(id)));
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getFollowing(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getFollowing(id)));
    }
}
