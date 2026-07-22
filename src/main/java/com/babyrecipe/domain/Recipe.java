package com.babyrecipe.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes", indexes = {
    @Index(name = "idx_recipe_created_at", columnList = "created_at"),
    @Index(name = "idx_recipe_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "cooking_time")
    private Integer cookingTime;

    private Integer servings;

    @Column(name = "view_count")
    private long viewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<RecipeStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "recipe_tags",
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private List<Tag> tags = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Like> likes = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum AgeGroup {
        MONTH_4_6("4~6개월"),
        MONTH_7_9("7~9개월"),
        MONTH_10_12("10~12개월"),
        MONTH_12_18("12~18개월"),
        MONTH_18_PLUS("18개월 이상");

        public final String label;
        AgeGroup(String label) { this.label = label; }
    }

    public enum Category {
        PORRIDGE("죽"), SOUP("국/찌개"), SIDE_DISH("반찬"),
        FINGER_FOOD("핑거푸드"), SNACK("간식"), DRINK("음료");

        public final String label;
        Category(String label) { this.label = label; }
    }

    @Builder
    public Recipe(String title, String description, String imageUrl, String sourceUrl, Integer cookingTime,
                  Integer servings, AgeGroup ageGroup, Category category, User author) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.sourceUrl = sourceUrl;
        this.cookingTime = cookingTime;
        this.servings = servings;
        this.ageGroup = ageGroup;
        this.category = category;
        this.author = author;
    }

    public void update(String title, String description, String imageUrl, String sourceUrl,
                       Integer cookingTime, Integer servings, AgeGroup ageGroup, Category category) {
        this.title = title;
        this.description = description;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (sourceUrl != null) this.sourceUrl = sourceUrl;
        this.cookingTime = cookingTime;
        this.servings = servings;
        this.ageGroup = ageGroup;
        this.category = category;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void addStep(RecipeStep step) {
        steps.add(step);
    }

    public void clearSteps() {
        steps.clear();
    }

    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);
    }

    public void clearIngredients() {
        ingredients.clear();
    }
}
