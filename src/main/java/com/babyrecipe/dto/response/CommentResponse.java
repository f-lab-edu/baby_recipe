package com.babyrecipe.dto.response;

import com.babyrecipe.domain.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CommentResponse {
    private Long id;
    private Long recipeId;
    private AuthorInfo author;
    private String content;
    private Long parentId;
    private List<CommentResponse> replies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter @Builder
    public static class AuthorInfo {
        private Long id;
        private String nickname;
        private String profileImage;
    }

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
            .id(comment.getId())
            .recipeId(comment.getRecipe().getId())
            .author(AuthorInfo.builder()
                .id(comment.getAuthor().getId())
                .nickname(comment.getAuthor().getNickname())
                .profileImage(comment.getAuthor().getProfileImage())
                .build())
            .content(comment.getContent())
            .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
            .replies(comment.getReplies().stream().map(CommentResponse::from).toList())
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }
}
