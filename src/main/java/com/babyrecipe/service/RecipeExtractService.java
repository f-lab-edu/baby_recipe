package com.babyrecipe.service;

import com.babyrecipe.dto.response.RecipeExtractResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
    private final ImageStorageService imageStorageService;

    private record PageContent(String text, List<String> imageEntries, List<String> rawImageUrls, String structuredContent) {
        PageContent(String text, List<String> imageEntries) {
            this(text, imageEntries, List.of(), "");
        }
        PageContent(String text, List<String> imageEntries, List<String> rawImageUrls) {
            this(text, imageEntries, rawImageUrls, "");
        }
    }

    // ── URL 기반 추출 ──────────────────────────────────────────────────────────

    public RecipeExtractResponse extract(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BabyRecipeException.badRequest("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        log.debug("레시피 추출 시작: url={}, model={}", url, model);
        PageContent page = fetchContent(url);
        String jsonText = callClaudeText(url, page);
        log.info("Claude 응답: {}", jsonText);
        RecipeExtractResponse result = parseResult(jsonText);
        if (page.structuredContent().isBlank()) {
            assignStepImagesSequentially(result, page.rawImageUrls());
        }
        return downloadExternalImages(result, url);
    }

    private void assignStepImagesSequentially(RecipeExtractResponse response, List<String> stepImageCandidates) {
        if (stepImageCandidates.isEmpty() || response.getSteps() == null) return;
        boolean allNull = response.getSteps().stream().allMatch(s -> s.getImageUrl() == null);
        if (!allNull) return;
        for (int i = 0; i < response.getSteps().size() && i < stepImageCandidates.size(); i++) {
            response.getSteps().get(i).setImageUrl(stepImageCandidates.get(i));
        }
        log.info("단계 이미지 순서 매핑: {}개", Math.min(response.getSteps().size(), stepImageCandidates.size()));
    }

    private RecipeExtractResponse downloadExternalImages(RecipeExtractResponse response, String pageUrl) {
        if (response.getImageUrl() != null && response.getImageUrl().startsWith("http")) {
            log.info("대표 이미지 다운로드: {}", response.getImageUrl());
            String local = imageStorageService.saveFromUrl(response.getImageUrl(), pageUrl);
            if (local != null) response.setImageUrl(local);
        }
        if (response.getSteps() != null) {
            for (RecipeExtractResponse.StepItem step : response.getSteps()) {
                if (step.getImageUrl() != null && step.getImageUrl().startsWith("http")) {
                    log.info("단계 이미지 다운로드: {}", step.getImageUrl());
                    String local = imageStorageService.saveFromUrl(step.getImageUrl(), pageUrl);
                    if (local != null) step.setImageUrl(local);
                }
            }
        }
        return response;
    }

    // ── 이미지 기반 추출 ───────────────────────────────────────────────────────

    public RecipeExtractResponse extractFromImages(List<MultipartFile> images) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BabyRecipeException.badRequest("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        if (images == null || images.isEmpty()) {
            throw BabyRecipeException.badRequest("이미지를 1장 이상 업로드해주세요.");
        }
        if (images.size() > 10) {
            throw BabyRecipeException.badRequest("이미지는 최대 10장까지 업로드 가능합니다.");
        }

        List<String> savedUrls = images.stream()
            .map(imageStorageService::save)
            .toList();

        String jsonText = callClaudeVision(images, savedUrls.size());
        log.debug("Claude Vision 응답: {}", jsonText);
        return parseVisionResult(jsonText, savedUrls);
    }

    // ── URL fetch ─────────────────────────────────────────────────────────────

    private PageContent fetchContent(String url) {
        try {
            if (isYouTube(url)) return fetchYouTubeMeta(url);
            String fetchUrl = toMobileUrl(url);
            Document doc = Jsoup.connect(fetchUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .referrer(url)
                .timeout(10_000).get();

            String ogImage = doc.select("meta[property=og:image]").attr("content");

            List<String> imageEntries;
            List<String> rawImageUrls = new ArrayList<>();
            String structuredContent = "";
            if (url.contains("blog.naver.com")) {
                imageEntries = collectNaverBlogImages(doc, ogImage);
                structuredContent = buildNaverStructuredContent(doc, ogImage);
                log.info("네이버 블로그 이미지 수집: {}장, 구조화 콘텐츠 길이: {}", imageEntries.size(), structuredContent.length());
            } else {
                imageEntries = new ArrayList<>();
                int idx = 1;
                if (!ogImage.isBlank()) {
                    imageEntries.add("[" + idx++ + "] " + ogImage + " (대표 이미지)");
                }
                for (org.jsoup.nodes.Element img : doc.select("img")) {
                    if (idx > 31) break;
                    String src = java.util.stream.Stream.of("src", "data-src", "data-lazy", "data-lazy-src", "data-original")
                        .map(img::attr)
                        .filter(s -> !s.isBlank() && !s.startsWith("data:"))
                        .findFirst()
                        .map(s -> s.startsWith("http") ? s : img.absUrl("src"))
                        .orElse("");
                    if (src.isBlank() || src.equals(ogImage)) continue;
                    String context = img.attr("alt").trim();
                    if (context.isBlank()) {
                        org.jsoup.nodes.Element el = img.parent();
                        outer:
                        for (int depth = 0; depth < 3 && el != null; depth++) {
                            String own = el.ownText().trim();
                            if (!own.isBlank() && own.length() < 120) { context = own; break; }
                            for (org.jsoup.nodes.Element sib : el.children()) {
                                if (sib.select("img").first() != null) continue;
                                String t = sib.text().trim();
                                if (!t.isBlank() && t.length() < 120) { context = t; break outer; }
                            }
                            el = el.parent();
                        }
                    }
                    if (context.length() > 80) context = context.substring(0, 80);
                    imageEntries.add("[" + idx++ + "] " + src + (context.isBlank() ? "" : " (" + context + ")"));
                }
            }

            String combined = "제목: " + doc.title()
                + "\n설명: " + doc.select("meta[name=description]").attr("content")
                + "\n본문:\n" + doc.body().text();
            String text = combined.length() > 6000 ? combined.substring(0, 6000) : combined;

            return new PageContent(text, imageEntries, rawImageUrls, structuredContent);
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("URL fetch 실패: {}", url, e);
            throw BabyRecipeException.badRequest("URL에서 내용을 가져올 수 없습니다. 접근이 차단된 사이트이거나 잘못된 URL일 수 있습니다.");
        }
    }

    private PageContent fetchYouTubeMeta(String url) {
        try {
            String oembedUrl = "https://www.youtube.com/oembed?url=" + url + "&format=json";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode oembedNode = objectMapper.readTree(res.body());
            String title = oembedNode.path("title").asText("");
            String thumbnailUrl = oembedNode.path("thumbnail_url").asText("");

            String metaDesc = "";
            try {
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; Googlebot/2.1)")
                    .timeout(8_000).get();
                metaDesc = doc.select("meta[name=description]").attr("content");
            } catch (Exception ignored) {}

            List<String> imageEntries = new ArrayList<>();
            if (!thumbnailUrl.isBlank()) {
                imageEntries.add("[1] " + thumbnailUrl + " (썸네일/완성본)");
            }

            return new PageContent("YouTube 영상 제목: " + title + "\n영상 설명: " + metaDesc, imageEntries);
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            throw BabyRecipeException.badRequest("YouTube URL에서 정보를 가져올 수 없습니다.");
        }
    }

    private boolean isYouTube(String url) {
        return url.contains("youtube.com") || url.contains("youtu.be");
    }

    private String toMobileUrl(String url) {
        // 네이버 블로그: blog.naver.com → m.blog.naver.com
        if (url.contains("blog.naver.com") && !url.contains("m.blog.naver.com")) {
            return url.replace("blog.naver.com", "m.blog.naver.com");
        }
        return url;
    }

    private String buildNaverStructuredContent(Document doc, String ogImage) {
        String mainBase = baseUrl(upgradeNaverImageQuality(ogImage));
        StringBuilder sb = new StringBuilder();
        int imgIdx = 1;
        for (org.jsoup.nodes.Element module : doc.select(".se-module")) {
            if (module.hasClass("se-module-text")) {
                String t = module.text().trim();
                if (!t.isBlank() && t.length() > 3) {
                    sb.append("T:").append(t, 0, Math.min(t.length(), 150)).append("\n");
                }
            } else if (module.hasClass("se-module-image")) {
                String src = java.util.stream.Stream.of("src", "data-lazy-src", "data-src", "data-original")
                    .map(a -> module.select("img").attr(a))
                    .filter(s -> !s.isBlank() && !s.startsWith("data:") && s.contains("pstatic.net"))
                    .findFirst().orElse("");
                if (!src.isBlank()) {
                    src = upgradeNaverImageQuality(src);
                    if (!baseUrl(src).equals(mainBase)) {
                        sb.append("I[").append(imgIdx++).append("]:").append(src).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private List<String> collectNaverBlogImages(Document doc, String ogImage) {
        List<String> entries = new ArrayList<>();
        int idx = 1;
        String mainImage = upgradeNaverImageQuality(ogImage);
        String mainBase = baseUrl(mainImage);
        if (!mainImage.isBlank()) {
            entries.add("[" + idx++ + "] " + mainImage + " (대표 이미지)");
        }
        List<String> stepCandidates = new ArrayList<>();
        for (org.jsoup.nodes.Element img : doc.select(".se-image-resource, .se_mediaImage, figure img, .postViewArea img, #postViewArea img, .se-module-image img")) {
            String src = java.util.stream.Stream.of("src", "data-lazy-src", "data-src", "data-original")
                .map(img::attr)
                .filter(s -> !s.isBlank() && !s.startsWith("data:") && s.contains("pstatic.net"))
                .findFirst().orElse("");
            if (src.isBlank()) continue;
            src = upgradeNaverImageQuality(src);
            // base URL 비교로 중복 제거
            if (baseUrl(src).equals(mainBase)) continue;
            if (!stepCandidates.contains(src)) stepCandidates.add(src);
        }
        // 파일명의 (N) 번호로 정렬 → 단계 순서와 일치
        stepCandidates.sort((a, b) -> extractSeqNumber(a) - extractSeqNumber(b));
        for (String src : stepCandidates) {
            if (idx > 31) break;
            entries.add("[" + idx++ + "] " + src);
        }
        return entries;
    }

    private String baseUrl(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private int extractSeqNumber(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("_\\((\\d+)\\)\\.").matcher(url);
        return m.find() ? Integer.parseInt(m.group(1)) : 999;
    }

    private String upgradeNaverImageQuality(String url) {
        if (url == null || url.isBlank()) return "";
        return url.replaceAll("[?&]type=w\\d+(_blur)?", "?type=w966");
    }

    // ── Claude API 호출 (텍스트) ───────────────────────────────────────────────

    private String callClaudeText(String url, PageContent page) {
        String imageSection = "";
        if (!page.structuredContent().isBlank()) {
            // 네이버 블로그: 텍스트-이미지 순서 구조 제공
            imageSection = "\n\n블로그 내용 순서 (T:텍스트, I[N]:이미지URL — 실제 블로그 순서 그대로):\n"
                + page.structuredContent()
                + "\n대표 이미지 URL: " + page.imageEntries().stream().findFirst().map(e -> e.replaceFirst("^\\[1\\] ", "").replaceFirst(" \\(.*\\)$", "")).orElse("null")
                + "\n\n각 조리 단계 설명과 같거나 가장 유사한 T: 줄을 찾고, 그 바로 앞이나 뒤의 I[N] URL을 해당 step의 imageUrl로 사용하세요. 없으면 null.";
        } else if (!page.imageEntries().isEmpty()) {
            imageSection = "\n\n페이지 이미지 목록 (번호 → URL (설명)):\n"
                + String.join("\n", page.imageEntries())
                + "\n\n위 목록에서 완성된 음식 사진의 URL을 \"imageUrl\"에 넣고,"
                + " 각 조리 단계에 맞는 이미지 URL을 step의 \"imageUrl\"에 넣어주세요. 없으면 null.";
        }

        String prompt = """
            당신은 이유식/아기 레시피 추출 전문가입니다.
            아래 웹페이지 내용에서 레시피 정보를 추출하여 JSON으로만 응답해주세요.

            URL: %s
            내용:
            %s%s

            다음 JSON 형식으로만 응답하세요 (다른 텍스트 없이):
            {
              "title": "레시피 제목",
              "description": "간단한 설명 (2~3문장)",
              "ageGroup": "MONTH_4_6 또는 MONTH_7_9 또는 MONTH_10_12 또는 MONTH_12_18 또는 MONTH_18_PLUS 중 하나",
              "category": "PORRIDGE 또는 SOUP 또는 SIDE_DISH 또는 FINGER_FOOD 또는 SNACK 또는 DRINK 중 하나",
              "cookingTime": 조리시간(분, 숫자 또는 null),
              "servings": 인분수(숫자 또는 null),
              "imageUrl": "완성본 이미지 URL (이미지 목록에서 선택, 없으면 null)",
              "ingredients": [{"name": "재료명", "amount": "양", "unit": "단위"}],
              "steps": [{"order": 1, "description": "조리 단계 설명", "imageUrl": "해당 단계 이미지 URL 또는 null"}],
              "tags": ["태그1", "태그2"]
            }

            연령 그룹: 4~6개월→MONTH_4_6, 7~9개월→MONTH_7_9, 10~12개월→MONTH_10_12, 12~18개월→MONTH_12_18, 18개월이상→MONTH_18_PLUS
            카테고리: 죽→PORRIDGE, 국찌개→SOUP, 반찬→SIDE_DISH, 핑거푸드→FINGER_FOOD, 간식→SNACK, 음료→DRINK
            반드시 단일 JSON 객체로만 응답하세요 (배열 [] 사용 금지).
            레시피를 찾을 수 없으면 null을 반환하세요.
            """.formatted(url, page.text(), imageSection);

        return callClaude(List.of(new ClaudeMessage("user", prompt)));
    }

    // ── Claude API 호출 (Vision) ───────────────────────────────────────────────

    private String callClaudeVision(List<MultipartFile> images, int count) {
        List<ContentBlock> blocks = new ArrayList<>();

        for (MultipartFile image : images) {
            try {
                String mediaType = image.getContentType() != null ? image.getContentType() : "image/jpeg";
                String base64 = Base64.getEncoder().encodeToString(image.getBytes());
                blocks.add(ContentBlock.image(mediaType, base64));
            } catch (Exception e) {
                log.error("이미지 base64 변환 실패", e);
                throw BabyRecipeException.badRequest("이미지를 읽을 수 없습니다.");
            }
        }

        String prompt = """
            당신은 이유식/아기 레시피 추출 전문가입니다.
            총 %d장의 이미지를 분석하여 레시피 정보를 추출해주세요.
            이미지에 번호를 1번부터 순서대로 붙입니다.

            완성된 음식이 접시에 담겨 플레이팅된 사진(완성본)이 있다면 해당 이미지 번호를 finishedImageIndex에 기재해주세요.
            각 조리 단계와 관련된 이미지가 있다면 해당 step의 stepImageIndex에 이미지 번호를 기재해주세요.

            다음 JSON 형식으로만 응답하세요 (다른 텍스트 없이):
            {
              "title": "레시피 제목",
              "description": "간단한 설명 (2~3문장)",
              "ageGroup": "MONTH_4_6 또는 MONTH_7_9 또는 MONTH_10_12 또는 MONTH_12_18 또는 MONTH_18_PLUS 중 하나",
              "category": "PORRIDGE 또는 SOUP 또는 SIDE_DISH 또는 FINGER_FOOD 또는 SNACK 또는 DRINK 중 하나",
              "cookingTime": 조리시간(분, 숫자 또는 null),
              "servings": 인분수(숫자 또는 null),
              "ingredients": [{"name": "재료명", "amount": "양", "unit": "단위"}],
              "steps": [{"order": 1, "description": "조리 단계 설명", "stepImageIndex": 이미지번호또는null}],
              "tags": ["태그1", "태그2"],
              "finishedImageIndex": 완성본이미지번호또는null
            }

            연령 그룹: 4~6개월→MONTH_4_6, 7~9개월→MONTH_7_9, 10~12개월→MONTH_10_12, 12~18개월→MONTH_12_18, 18개월이상→MONTH_18_PLUS
            카테고리: 죽→PORRIDGE, 국찌개→SOUP, 반찬→SIDE_DISH, 핑거푸드→FINGER_FOOD, 간식→SNACK, 음료→DRINK
            반드시 단일 JSON 객체로만 응답하세요 (배열 [] 사용 금지).
            """.formatted(count);

        blocks.add(ContentBlock.text(prompt));
        return callClaude(List.of(new ClaudeMessage("user", blocks)));
    }

    // ── 공통 Claude HTTP 호출 ──────────────────────────────────────────────────

    private String callClaude(List<ClaudeMessage> messages) {
        try {
            ClaudeRequestBody body = new ClaudeRequestBody(model, 2048, messages);
            String requestJson = objectMapper.writeValueAsString(body);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(120))
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

    // ── 파싱 ──────────────────────────────────────────────────────────────────

    private RecipeExtractResponse parseResult(String jsonText) {
        try {
            JsonNode node = extractJsonNode(jsonText);
            return objectMapper.treeToValue(node, RecipeExtractResponse.class);
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.error("레시피 JSON 파싱 실패: {}", jsonText, e);
            throw BabyRecipeException.badRequest("레시피 정보를 추출할 수 없습니다.");
        }
    }

    private RecipeExtractResponse parseVisionResult(String jsonText, List<String> savedUrls) {
        try {
            JsonNode node = extractJsonNode(jsonText);

            // finishedImageIndex → imageUrl 매핑 (1-based)
            Integer finishedIdx = node.hasNonNull("finishedImageIndex")
                ? node.get("finishedImageIndex").asInt() : null;

            RecipeExtractResponse response = objectMapper.treeToValue(node, RecipeExtractResponse.class);

            if (finishedIdx != null && finishedIdx >= 1 && finishedIdx <= savedUrls.size()) {
                response.setImageUrl(savedUrls.get(finishedIdx - 1));
            } else {
                response.setImageUrl(savedUrls.get(0));
            }

            // stepImageIndex → step.imageUrl 매핑
            if (response.getSteps() != null) {
                JsonNode stepsNode = node.get("steps");
                for (int i = 0; i < response.getSteps().size(); i++) {
                    JsonNode stepNode = stepsNode != null ? stepsNode.get(i) : null;
                    if (stepNode != null && stepNode.hasNonNull("stepImageIndex")) {
                        int stepImgIdx = stepNode.get("stepImageIndex").asInt();
                        if (stepImgIdx >= 1 && stepImgIdx <= savedUrls.size()) {
                            response.getSteps().get(i).setImageUrl(savedUrls.get(stepImgIdx - 1));
                        }
                    }
                }
            }

            return response;
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vision 레시피 JSON 파싱 실패: {}", jsonText, e);
            throw BabyRecipeException.badRequest("이미지에서 레시피 정보를 추출할 수 없습니다.");
        }
    }

    private JsonNode extractJsonNode(String jsonText) throws Exception {
        String cleaned = jsonText.trim();

        Matcher fenceMatcher = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(cleaned);
        if (fenceMatcher.find()) {
            cleaned = fenceMatcher.group(1).trim();
        } else {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
        }

        if (cleaned.equals("null") || cleaned.isBlank()) {
            throw BabyRecipeException.badRequest("레시피 정보를 찾을 수 없습니다.");
        }

        cleaned = repairJson(cleaned);

        JsonNode node = objectMapper.readTree(cleaned);
        if (node.isArray()) {
            log.warn("Claude가 배열로 응답함 - 첫 번째 요소 사용");
            if (node.isEmpty()) throw BabyRecipeException.badRequest("레시피 정보를 찾을 수 없습니다.");
            node = node.get(0);
        }
        return node;
    }

    // 배열 요소 사이 누락된 쉼표 보정 (Claude가 간헐적으로 생략하는 케이스 처리)
    private String repairJson(String json) {
        return json.replaceAll("\\}(\\s*)(\\{)", "},$1$2")
                   .replaceAll("\\](\\s*)(\\[)", "],$1$2");
    }

    // ── 내부 DTO ──────────────────────────────────────────────────────────────

    @Data
    static class ClaudeRequestBody {
        private String model;
        @JsonProperty("max_tokens")
        private int maxTokens;
        private List<ClaudeMessage> messages;

        ClaudeRequestBody(String model, int maxTokens, List<ClaudeMessage> messages) {
            this.model = model;
            this.maxTokens = maxTokens;
            this.messages = messages;
        }
    }

    @Data
    static class ClaudeMessage {
        private String role;
        private Object content; // String (text-only) or List<ContentBlock> (vision)

        ClaudeMessage(String role, String textContent) {
            this.role = role;
            this.content = textContent;
        }

        ClaudeMessage(String role, List<ContentBlock> blocks) {
            this.role = role;
            this.content = blocks;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ContentBlock {
        private String type;
        private String text;
        private ImageSource source;

        static ContentBlock text(String text) {
            ContentBlock b = new ContentBlock();
            b.type = "text";
            b.text = text;
            return b;
        }

        static ContentBlock image(String mediaType, String base64Data) {
            ContentBlock b = new ContentBlock();
            b.type = "image";
            b.source = new ImageSource(mediaType, base64Data);
            return b;
        }
    }

    @Data
    static class ImageSource {
        private final String type = "base64";
        @JsonProperty("media_type")
        private String mediaType;
        private String data;

        ImageSource(String mediaType, String data) {
            this.mediaType = mediaType;
            this.data = data;
        }
    }
}
