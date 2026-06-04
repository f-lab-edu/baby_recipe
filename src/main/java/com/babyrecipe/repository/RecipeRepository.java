package com.babyrecipe.repository;

import com.babyrecipe.domain.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT r FROM Recipe r JOIN FETCH r.author " +
           "WHERE (:ageGroup IS NULL OR r.ageGroup = :ageGroup) " +
           "AND (:category IS NULL OR r.category = :category) " +
           "AND (:keyword IS NULL OR r.title LIKE %:keyword%)")
    Page<Recipe> findWithFilters(
        @Param("ageGroup") Recipe.AgeGroup ageGroup,
        @Param("category") Recipe.Category category,
        @Param("keyword") String keyword,
        Pageable pageable);

    @Query("SELECT r FROM Recipe r JOIN FETCH r.author " +
           "WHERE r.author.id IN " +
           "(SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId)")
    Page<Recipe> findFeedByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(l) FROM Like l WHERE l.recipe.id = :recipeId")
    long countLikes(@Param("recipeId") Long recipeId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.recipe.id = :recipeId AND c.parent IS NULL")
    long countComments(@Param("recipeId") Long recipeId);

    @Query("SELECT r FROM Recipe r " +
           "JOIN r.ingredients ri " +
           "JOIN ri.ingredient i " +
           "WHERE i.name LIKE %:ingredientName%")
    Page<Recipe> findByIngredientName(@Param("ingredientName") String ingredientName, Pageable pageable);

    List<Recipe> findByAuthorId(Long authorId);
}
