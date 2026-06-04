package com.babyrecipe.service;

import com.babyrecipe.domain.Comment;
import com.babyrecipe.domain.Recipe;
import com.babyrecipe.domain.User;
import com.babyrecipe.dto.request.CommentRequest;
import com.babyrecipe.dto.response.CommentResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.CommentRepository;
import com.babyrecipe.repository.RecipeRepository;
import com.babyrecipe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    public Page<CommentResponse> getComments(Long recipeId, Pageable pageable) {
        return commentRepository.findRootCommentsByRecipeId(recipeId, pageable)
            .map(CommentResponse::from);
    }

    @Transactional
    public CommentResponse addComment(Long recipeId, CommentRequest request, Long userId) {
        Recipe recipe = recipeRepository.findById(recipeId)
            .orElseThrow(() -> BabyRecipeException.notFound("레시피"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                .orElseThrow(() -> BabyRecipeException.notFound("댓글"));
            if (!parent.getRecipe().getId().equals(recipeId)) {
                throw BabyRecipeException.badRequest("댓글이 해당 레시피에 속하지 않습니다.");
            }
        }

        Comment comment = Comment.builder()
            .recipe(recipe).author(user)
            .parent(parent).content(request.getContent())
            .build();
        commentRepository.save(comment);
        return CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(Long recipeId, Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> BabyRecipeException.notFound("댓글"));

        if (!comment.getRecipe().getId().equals(recipeId)) {
            throw BabyRecipeException.badRequest("댓글이 해당 레시피에 속하지 않습니다.");
        }
        if (!comment.getAuthor().getId().equals(userId)) {
            throw BabyRecipeException.forbidden();
        }
        commentRepository.delete(comment);
    }
}
