package com.babyrecipe.exception;

import org.springframework.http.HttpStatus;

public class BabyRecipeException extends RuntimeException {

    private final HttpStatus status;

    public BabyRecipeException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BabyRecipeException notFound(String resource) {
        return new BabyRecipeException(resource + "을(를) 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }

    public static BabyRecipeException forbidden() {
        return new BabyRecipeException("권한이 없습니다.", HttpStatus.FORBIDDEN);
    }

    public static BabyRecipeException conflict(String message) {
        return new BabyRecipeException(message, HttpStatus.CONFLICT);
    }

    public static BabyRecipeException badRequest(String message) {
        return new BabyRecipeException(message, HttpStatus.BAD_REQUEST);
    }
}
