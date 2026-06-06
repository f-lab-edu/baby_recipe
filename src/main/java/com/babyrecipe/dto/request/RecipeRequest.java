package com.babyrecipe.dto.request;

import com.babyrecipe.domain.Recipe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RecipeRequest {

    @NotBlank @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String description;

    @NotNull
    private Recipe.AgeGroup ageGroup;

    @NotNull
    private Recipe.Category category;

    private Integer cookingTime;
    private Integer servings;

    @Valid
    private List<IngredientItem> ingredients;

    @Valid
    private List<StepItem> steps;

    private List<String> tags;

    @Getter
    public static class IngredientItem {
        @NotBlank
        private String name;
        private String amount;
        private String unit;
    }

    @Getter
    public static class StepItem {
        @NotNull
        private Integer order;
        @NotBlank @Size(max = 1000)
        private String description;
        private String imageUrl;
    }
}
