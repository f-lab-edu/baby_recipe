package com.babyrecipe.dto.response;

import com.babyrecipe.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImage;
    private String bio;
    private long followerCount;
    private long followingCount;
    private long recipeCount;
    private boolean isFollowing;
    private LocalDateTime createdAt;

    public static UserResponse of(User user, long followerCount, long followingCount, boolean isFollowing) {
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .profileImage(user.getProfileImage())
            .bio(user.getBio())
            .followerCount(followerCount)
            .followingCount(followingCount)
            .recipeCount(user.getRecipes().size())
            .isFollowing(isFollowing)
            .createdAt(user.getCreatedAt())
            .build();
    }
}
