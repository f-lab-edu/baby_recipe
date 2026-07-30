package com.babyrecipe.repository;

import com.babyrecipe.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.following.id = :userId")
    long countFollowers(@Param("userId") Long userId);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.follower.id = :userId")
    long countFollowing(@Param("userId") Long userId);

    @Query("SELECT u FROM User u WHERE u.nickname LIKE %:keyword% " +
           "AND (:excludeId IS NULL OR u.id <> :excludeId)")
    Page<User> searchByNickname(
        @Param("keyword") String keyword,
        @Param("excludeId") Long excludeId,
        Pageable pageable);
}
