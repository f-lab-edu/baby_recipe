package com.babyrecipe.service;

import com.babyrecipe.domain.Follow;
import com.babyrecipe.domain.User;
import com.babyrecipe.dto.request.UpdateProfileRequest;
import com.babyrecipe.dto.response.UserResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.FollowRepository;
import com.babyrecipe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse getUser(Long targetId, Long currentUserId) {
        User user = findUser(targetId);
        long followerCount = userRepository.countFollowers(targetId);
        long followingCount = userRepository.countFollowing(targetId);
        boolean isFollowing = currentUserId != null
            && followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetId);
        return UserResponse.of(user, followerCount, followingCount, isFollowing);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        if (request.getNickname() != null && !request.getNickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw BabyRecipeException.conflict("이미 사용 중인 닉네임입니다.");
            }
        }
        user.updateProfile(request.getNickname(), request.getBio(), null);

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw BabyRecipeException.badRequest("현재 비밀번호를 입력해주세요.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw BabyRecipeException.badRequest("현재 비밀번호가 올바르지 않습니다.");
            }
            user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        }

        return UserResponse.of(user,
            userRepository.countFollowers(userId),
            userRepository.countFollowing(userId),
            false);
    }

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw BabyRecipeException.badRequest("자기 자신을 팔로우할 수 없습니다.");
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw BabyRecipeException.conflict("이미 팔로우한 사용자입니다.");
        }
        User follower = findUser(followerId);
        User following = findUser(followingId);
        followRepository.save(Follow.builder().follower(follower).following(following).build());
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
            .orElseThrow(() -> BabyRecipeException.notFound("팔로우 관계"));
        followRepository.delete(follow);
    }

    public Page<UserResponse> searchUsers(String keyword, Long currentUserId, Pageable pageable) {
        return userRepository.searchByNickname(keyword, currentUserId, pageable)
            .map(u -> UserResponse.of(u,
                userRepository.countFollowers(u.getId()),
                userRepository.countFollowing(u.getId()),
                currentUserId != null
                    && followRepository.existsByFollowerIdAndFollowingId(currentUserId, u.getId())));
    }

    public List<UserResponse> getFollowers(Long userId) {
        return followRepository.findFollowersByUserId(userId).stream()
            .map(u -> UserResponse.of(u, 0, 0, false))
            .toList();
    }

    public List<UserResponse> getFollowing(Long userId) {
        return followRepository.findFollowingByUserId(userId).stream()
            .map(u -> UserResponse.of(u, 0, 0, false))
            .toList();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));
    }
}
