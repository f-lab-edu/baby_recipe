package com.babyrecipe.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class RecipeExtractResponse {
    private String title;
    private String description;
    private String ageGroup;
    private String category;
    private Integer cookingTime;
    private Integer servings;
    private String imageUrl;
    private List<String> tags;
    private List<IngredientItem> ingredients;
    private List<StepItem> steps;

    @Data
    public static class IngredientItem {
        private String name;
        private String amount;
        private String unit;
    }

    @Data
    public static class StepItem {
        private Integer order;
        private String description;
    }
}
