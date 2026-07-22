package com.babyrecipe.dto.response;

import com.babyrecipe.domain.Recipe;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RecipeResponse {
    private Long id;
    private String title;
    private String description;
    private String imageUrl;
    private String sourceUrl;
    private Integer cookingTime;
    private Integer servings;
    private long viewCount;
    private String ageGroup;
    private String ageGroupLabel;
    private String category;
    private String categoryLabel;
    private AuthorInfo author;
    private List<IngredientInfo> ingredients;
    private List<StepInfo> steps;
    private List<String> tags;
    private long likeCount;
    private long commentCount;
    private boolean liked;
    private boolean bookmarked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter @Builder
    public static class AuthorInfo {
        private Long id;
        private String nickname;
        private String profileImage;
    }

    @Getter @Builder
    public static class IngredientInfo {
        private String name;
        private String amount;
        private String unit;
    }

    @Getter @Builder
    public static class StepInfo {
        private int order;
        private String description;
        private String imageUrl;
    }

    public static RecipeResponse from(Recipe recipe, long likeCount, long commentCount,
                                      boolean liked, boolean bookmarked) {
        return RecipeResponse.builder()
            .id(recipe.getId())
            .title(recipe.getTitle())
            .description(recipe.getDescription())
            .imageUrl(recipe.getImageUrl())
            .sourceUrl(recipe.getSourceUrl())
            .cookingTime(recipe.getCookingTime())
            .servings(recipe.getServings())
            .viewCount(recipe.getViewCount())
            .ageGroup(recipe.getAgeGroup().name())
            .ageGroupLabel(recipe.getAgeGroup().label)
            .category(recipe.getCategory().name())
            .categoryLabel(recipe.getCategory().label)
            .author(AuthorInfo.builder()
                .id(recipe.getAuthor().getId())
                .nickname(recipe.getAuthor().getNickname())
                .profileImage(recipe.getAuthor().getProfileImage())
                .build())
            .ingredients(recipe.getIngredients().stream()
                .map(ri -> IngredientInfo.builder()
                    .name(ri.getIngredient().getName())
                    .amount(ri.getAmount())
                    .unit(ri.getUnit())
                    .build())
                .toList())
            .steps(recipe.getSteps().stream()
                .map(s -> StepInfo.builder()
                    .order(s.getStepOrder())
                    .description(s.getDescription())
                    .imageUrl(s.getImageUrl())
                    .build())
                .toList())
            .tags(recipe.getTags().stream().map(t -> t.getName()).toList())
            .likeCount(likeCount)
            .commentCount(commentCount)
            .liked(liked)
            .bookmarked(bookmarked)
            .createdAt(recipe.getCreatedAt())
            .updatedAt(recipe.getUpdatedAt())
            .build();
    }
}
