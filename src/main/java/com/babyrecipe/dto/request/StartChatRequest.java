package com.babyrecipe.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartChatRequest {

    @NotNull
    private Long partnerId;
}
