package com.babyrecipe.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateProfileRequest {

    @Size(min = 2, max = 20)
    private String nickname;

    @Size(max = 200)
    private String bio;

    private String currentPassword;

    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String newPassword;
}
