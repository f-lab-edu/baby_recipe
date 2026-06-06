package com.babyrecipe.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecipeExtractRequest {

    @NotBlank(message = "URL을 입력해주세요.")
    private String url;
}
