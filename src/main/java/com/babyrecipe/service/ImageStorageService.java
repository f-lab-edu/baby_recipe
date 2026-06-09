package com.babyrecipe.service;

import com.babyrecipe.exception.BabyRecipeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ImageStorageService {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String save(MultipartFile file) {
        validate(file);
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String ext = extractExtension(file.getOriginalFilename(), file.getContentType());
            String filename = UUID.randomUUID() + "." + ext;
            file.transferTo(dir.resolve(filename));

            return baseUrl + "/uploads/" + filename;
        } catch (IOException e) {
            log.error("이미지 저장 실패", e);
            throw BabyRecipeException.badRequest("이미지 저장에 실패했습니다.");
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw BabyRecipeException.badRequest("빈 파일은 업로드할 수 없습니다.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw BabyRecipeException.badRequest("jpg, png, webp 형식만 업로드 가능합니다.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BabyRecipeException.badRequest("파일 크기는 10MB를 초과할 수 없습니다.");
        }
    }

    private String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
