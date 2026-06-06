package com.babyrecipe.service;

import com.babyrecipe.dto.response.RecipeExtractResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeExtractService {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.model}")
    private String model;

    private final ObjectMapper objectMapper;

    public RecipeExtractResponse extract(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BabyRecipeException.badRequest("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        String content = fetchContent(url);
        String jsonText = callClaude(url, content);
        return parseResult(jsonText);
    }

    private String fetchContent(String url) {
        try {
            if (isYouTube(url)) {
                return fetchYouTubeMeta(url);
            }
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10_000)
                .get();
            String combined = "제목: " + doc.title()
                + "\n설명: " + doc.select("meta[name=description]").attr("content")
                + "\n본문:\n" + doc.body().text();
            return combined.length() > 6000 ? combined.substring(0, 6000) : combined;
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("URL fetch 실패: {}", url, e);
            throw BabyRecipeException.badRequest("URL에서 내용을 가져올 수 없습니다. 접근이 차단된 사이트이거나 잘못된 URL일 수 있습니다.");
        }
    }

    private String fetchYouTubeMeta(String url) {
        try {
            String oembedUrl = "https://www.youtube.com/oembed?url=" + url + "&format=json";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String title = objectMapper.readTree(res.body()).path("title").asText("");

            String metaDesc = "";
            try {
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; Googlebot/2.1)")
                    .timeout(8_000).get();
                metaDesc = doc.select("meta[name=description]").attr("content");
            } catch (Exception ignored) {}

            return "YouTube 영상 제목: " + title + "\n영상 설명: " + metaDesc;
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            throw BabyRecipeException.badRequest("YouTube URL에서 정보를 가져올 수 없습니다.");
        }
    }

    private boolean isYouTube(String url) {
        return url.contains("youtube.com") || url.contains("youtu.be");
    }

    private String callClaude(String url, String content) {
        String prompt = """
            당신은 이유식/아기 레시피 추출 전문가입니다.
            아래 웹페이지 내용에서 레시피 정보를 추출하여 JSON으로만 응답해주세요.

            URL: %s
            내용:
            %s

            다음 JSON 형식으로만 응답하세요 (다른 텍스트 없이):
            {
              "title": "레시피 제목",
              "description": "간단한 설명 (2~3문장)",
              "ageGroup": "MONTH_4_6 또는 MONTH_7_9 또는 MONTH_10_12 또는 MONTH_12_18 또는 MONTH_18_PLUS 중 하나",
              "category": "PORRIDGE 또는 SOUP 또는 SIDE_DISH 또는 FINGER_FOOD 또는 SNACK 또는 DRINK 중 하나",
              "cookingTime": 조리시간(분, 숫자 또는 null),
              "servings": 인분수(숫자 또는 null),
              "ingredients": [{"name": "재료명", "amount": "양", "unit": "단위"}],
              "steps": [{"order": 1, "description": "조리 단계 설명"}],
              "tags": ["태그1", "태그2"]
            }

            연령 그룹: 4~6개월→MONTH_4_6, 7~9개월→MONTH_7_9, 10~12개월→MONTH_10_12, 12~18개월→MONTH_12_18, 18개월이상→MONTH_18_PLUS
            카테고리: 죽→PORRIDGE, 국찌개→SOUP, 반찬→SIDE_DISH, 핑거푸드→FINGER_FOOD, 간식→SNACK, 음료→DRINK
            레시피를 찾을 수 없으면 null을 반환하세요.
            """.formatted(url, content);

        try {
            ClaudeRequestBody body = new ClaudeRequestBody();
            body.setModel(model);
            body.setMaxTokens(2048);
            body.setMessages(List.of(new ClaudeRequestBody.Message("user", prompt)));

            String requestJson = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Claude API 오류: {} {}", response.statusCode(), response.body());
                throw new BabyRecipeException("AI 분석 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("content").get(0).path("text").asText();

        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            throw new BabyRecipeException("AI 분석 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private RecipeExtractResponse parseResult(String jsonText) {
        try {
            String cleaned = jsonText.trim();

            // 코드블록 안의 JSON 추출
            Matcher fenceMatcher = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(cleaned);
            if (fenceMatcher.find()) {
                cleaned = fenceMatcher.group(1).trim();
            } else {
                // 코드블록 없이 앞뒤 텍스트가 붙은 경우 첫 { ~ 마지막 } 추출
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');
                if (start != -1 && end != -1 && end > start) {
                    cleaned = cleaned.substring(start, end + 1);
                }
            }

            if (cleaned.equals("null") || cleaned.isBlank()) {
                throw BabyRecipeException.badRequest("레시피 정보를 찾을 수 없습니다.");
            }
            return objectMapper.readValue(cleaned, RecipeExtractResponse.class);
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.error("레시피 JSON 파싱 실패: {}", jsonText, e);
            throw BabyRecipeException.badRequest("레시피 정보를 추출할 수 없습니다.");
        }
    }

    @Data
    static class ClaudeRequestBody {
        private String model;
        @JsonProperty("max_tokens")
        private int maxTokens;
        private List<Message> messages;

        @Data
        static class Message {
            private String role;
            private String content;
            Message(String role, String content) {
                this.role = role;
                this.content = content;
            }
        }
    }
}
