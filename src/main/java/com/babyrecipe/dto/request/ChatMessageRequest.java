package com.babyrecipe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequest {

    @NotNull
    private Long roomId;

    @NotBlank
    @Size(max = 1000)
    private String content;
}
