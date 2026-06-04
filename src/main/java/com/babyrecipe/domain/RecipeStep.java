package com.babyrecipe.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recipe_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Builder
    public RecipeStep(Recipe recipe, int stepOrder, String description, String imageUrl) {
        this.recipe = recipe;
        this.stepOrder = stepOrder;
        this.description = description;
        this.imageUrl = imageUrl;
    }
}
