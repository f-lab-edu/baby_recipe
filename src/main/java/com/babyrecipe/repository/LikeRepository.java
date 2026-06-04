package com.babyrecipe.repository;

import com.babyrecipe.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByRecipeIdAndUserId(Long recipeId, Long userId);
    boolean existsByRecipeIdAndUserId(Long recipeId, Long userId);
}
