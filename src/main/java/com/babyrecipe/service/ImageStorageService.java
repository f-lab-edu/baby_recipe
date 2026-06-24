package com.babyrecipe.service;

import com.babyrecipe.exception.BabyRecipeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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

    public String saveFromUrl(String url) {
        return saveFromUrl(url, null);
    }

    public String saveFromUrl(String imageUrl, String pageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        try {
            String referer = (pageUrl != null && !pageUrl.isBlank()) ? pageUrl : imageUrl;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", referer)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .GET()
                .build();
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                log.warn("외부 이미지 다운로드 실패 (HTTP {}): {}", res.statusCode(), imageUrl);
                return null;
            }

            String contentType = res.headers().firstValue("Content-Type").orElse("image/jpeg");
            if (contentType.contains(";")) contentType = contentType.split(";")[0].trim();
            if (!ALLOWED_TYPES.contains(contentType)) {
                String lower = imageUrl.toLowerCase();
                if (lower.contains(".png")) contentType = "image/png";
                else if (lower.contains(".webp")) contentType = "image/webp";
                else contentType = "image/jpeg";
            }
            if (!ALLOWED_TYPES.contains(contentType)) {
                log.warn("외부 이미지 타입 불가: {} ({})", contentType, imageUrl);
                return null;
            }

            byte[] data = res.body();
            if (data.length == 0 || data.length > MAX_SIZE) {
                log.warn("외부 이미지 크기 불가 ({}bytes): {}", data.length, imageUrl);
                return null;
            }

            String ext = switch (contentType) {
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> "jpg";
            };

            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(filename), data);

            return baseUrl + "/uploads/" + filename;
        } catch (Exception e) {
            log.warn("외부 이미지 다운로드 실패: {}", imageUrl, e);
            return null;
        }
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String filename = imageUrl.substring(imageUrl.lastIndexOf("/uploads/") + 9);
            Path target = Paths.get(uploadDir).resolve(filename).normalize();
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("이미지 파일 삭제 실패: {}", imageUrl, e);
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
