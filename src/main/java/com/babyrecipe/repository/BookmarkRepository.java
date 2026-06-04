package com.babyrecipe.repository;

import com.babyrecipe.domain.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByRecipeIdAndUserId(Long recipeId, Long userId);
    boolean existsByRecipeIdAndUserId(Long recipeId, Long userId);

    @Query("SELECT b FROM Bookmark b JOIN FETCH b.recipe r JOIN FETCH r.author WHERE b.user.id = :userId")
    Page<Bookmark> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
