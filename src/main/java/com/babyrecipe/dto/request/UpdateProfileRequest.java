package com.babyrecipe.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateProfileRequest {

    @Size(min = 2, max = 20)
    private String nickname;

    @Size(max = 200)
    private String bio;
}
