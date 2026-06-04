package com.babyrecipe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CommentRequest {

    @NotBlank @Size(max = 1000)
    private String content;

    private Long parentId;
}
