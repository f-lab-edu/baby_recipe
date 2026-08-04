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

    public List<RecipeExtractResponse> extract(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            throw BabyRecipeException.badRequest("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        log.debug("레시피 추출 시작: url={}, model={}", url, model);
        try {
            PageContent page = fetchContent(url);
            String jsonText = callClaudeText(url, page);
            log.info("Claude 응답: {}", jsonText);
            List<RecipeExtractResponse> results = parseResult(jsonText);
            for (RecipeExtractResponse result : results) {
                if (page.structuredContent().isBlank()) {
                    assignStepImagesSequentially(result, page.rawImageUrls());
                }
                downloadExternalImages(result, url);
                result.setSourceUrl(url);
            }
            return results;
        } catch (Exception e) {
            log.warn("URL 자동 추출 실패, 링크만 저장하는 폴백으로 전환: url={}", url, e);
            return List.of(buildUrlOnlyFallback(url));
        }
    }

    private RecipeExtractResponse buildUrlOnlyFallback(String url) {
        RecipeExtractResponse r = new RecipeExtractResponse();
        r.setTitle(guessFallbackTitle(url));
        r.setSourceUrl(url);
        r.setIngredients(List.of());
        r.setSteps(List.of());
        r.setTags(List.of());
        return r;
    }

    // 정상 추출이 실패했을 때 제목만이라도 최대한 사람이 구분할 수 있게 채운다.
    private String guessFallbackTitle(String url) {
        String quickTitle = fetchQuickTitle(url);
        if (quickTitle != null && !quickTitle.isBlank()) {
            return quickTitle.length() > 100 ? quickTitle.substring(0, 100) : quickTitle;
        }
        String shortId = extractPathId(url);
        String label = guessSourceLabel(url);
        return shortId.isBlank() ? label + "에서 가져온 레시피" : label + "에서 가져온 레시피 (" + shortId + ")";
    }

    // 크롤링이 차단된 사이트(인스타그램 등)도 소셜 미리보기용 크롤러 UA에는
    // og:title을 내려주는 경우가 많아, 정식 추출과 별개로 제목만 가볍게 시도한다.
    private String fetchQuickTitle(String url) {
        try {
            Document doc = Jsoup.connect(url)
                .userAgent("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                .timeout(6_000).get();
            String ogTitle = doc.select("meta[property=og:title]").attr("content");
            if (!ogTitle.isBlank()) return ogTitle.trim();
            String title = doc.title();
            return title.isBlank() ? null : title.trim();
        } catch (Exception e) {
            log.debug("빠른 제목 조회 실패: {}", url, e);
            return null;
        }
    }

    // URL 마지막 경로 조각(게시물 코드 등)을 제목 뒤에 붙여 최소한의 구분자로 사용한다.
    private String extractPathId(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null) return "";
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isBlank()) {
                    String seg = segments[i].length() > 12 ? segments[i].substring(0, 12) : segments[i];
                    return seg;
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String guessSourceLabel(String url) {
        if (url.contains("instagram.com")) return "인스타그램";
        if (url.contains("tiktok.com")) return "틱톡";
        if (url.contains("youtube.com") || url.contains("youtu.be")) return "유튜브";
        if (url.contains("blog.naver.com")) return "네이버 블로그";
        return "웹페이지";
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

    public List<RecipeExtractResponse> extractFromImages(List<MultipartFile> images) {
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
                + "\n페이지 대표(og:image) 이미지 URL: " + page.imageEntries().stream().findFirst().map(e -> e.replaceFirst("^\\[1\\] ", "").replaceFirst(" \\(.*\\)$", "")).orElse("null")
                + "\n\n이미지 매칭 규칙:\n"
                + "- 레시피가 1개뿐이면 위 '페이지 대표 이미지'를 그 레시피의 imageUrl로 사용하세요.\n"
                + "- 레시피가 여러 개면 페이지 대표 이미지는 무시하세요. 이런 블로그는 보통 [I[N] 이미지] → [레시피 제목/캡션 T:] → [레시피 본문 T:] 순서가 레시피 개수만큼 반복됩니다 — 즉 사진은 자기 레시피의 제목/캡션보다 항상 먼저(위에) 나옵니다. 각 레시피의 imageUrl은 그 레시피의 제목/캡션 T: 줄보다 앞에 있는 I[N] 중 가장 가까운(바로 직전) 것을 사용하세요. 레시피 본문이 끝난 뒤에 나오는 I[N]은 이미 다음 레시피의 이미지이므로 현재 레시피의 imageUrl로 쓰면 안 됩니다. 예를 들어 레시피가 4개면 I[N]도 보통 4개이고, N번째 이미지가 N번째 레시피의 것입니다.\n"
                + "- 각 조리 단계는 같거나 가장 유사한 T: 줄을 찾고, 그 줄 바로 앞이나 뒤의 I[N] 중 이미 다른 레시피의 대표 이미지로 쓰이지 않은 것을 step의 imageUrl로 사용하세요. 없으면 null.";
        } else if (!page.imageEntries().isEmpty()) {
            imageSection = "\n\n페이지 이미지 목록 (번호 → URL (설명)):\n"
                + String.join("\n", page.imageEntries())
                + "\n\n위 목록에서 완성된 음식 사진의 URL을 \"imageUrl\"에 넣고,"
                + " 각 조리 단계에 맞는 이미지 URL을 step의 \"imageUrl\"에 넣어주세요. 없으면 null.";
        }

        String prompt = """
            당신은 이유식/아기 레시피 추출 전문가입니다.
            아래 웹페이지 내용에서 레시피 정보를 추출하여 JSON으로만 응답해주세요.
            페이지 안에 서로 다른 레시피가 여러 개 있으면 각각을 배열의 별도 요소로 추출하세요.

            URL: %s
            내용:
            %s%s

            다음 JSON 배열 형식으로만 응답하세요 (다른 텍스트 없이). 레시피가 하나면 요소가 1개인 배열로 응답하세요:
            [
              {
                "title": "레시피 제목",
                "description": "간단한 설명 (2~3문장)",
                "ageGroup": "BABY_FOOD 또는 ADULT_FOOD 중 하나",
                "category": "PORRIDGE 또는 SOUP 또는 SIDE_DISH 또는 FINGER_FOOD 또는 SNACK 또는 DRINK 중 하나",
                "cookingTime": 조리시간(분, 숫자 또는 null),
                "servings": 인분수(숫자 또는 null),
                "imageUrl": "완성본 이미지 URL (이미지 목록에서 선택, 없으면 null)",
                "ingredients": [{"name": "재료명", "amount": "양", "unit": "단위"}],
                "steps": [{"order": 1, "description": "조리 단계 설명", "imageUrl": "해당 단계 이미지 URL 또는 null"}],
                "tags": ["태그1", "태그2"]
              }
            ]

            연령 그룹: 이유식(영아용, 18개월 미만 기준)→BABY_FOOD, 어른 음식(가족 식사 등 일반 요리)→ADULT_FOOD
            카테고리: 죽→PORRIDGE, 국찌개→SOUP, 반찬→SIDE_DISH, 핑거푸드→FINGER_FOOD, 간식→SNACK, 음료→DRINK
            반드시 JSON 배열로만 응답하세요 (배열을 감싸지 않은 단일 객체 금지).
            레시피를 찾을 수 없으면 빈 배열 []을 반환하세요.
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
            사진들 안에 서로 다른 레시피가 섞여 있다면 각 레시피를 배열의 별도 요소로 분리해서 응답하세요.
            (예: 레시피 카드/블로그 캡처를 여러 장 찍어서 올린 경우) 하나의 레시피에 대한 사진들이면 요소가 1개인 배열로 응답하세요.

            완성된 음식이 접시에 담겨 플레이팅된 사진(완성본)이 있다면 해당 이미지 번호를 finishedImageIndex에 기재해주세요.
            각 조리 단계와 관련된 이미지가 있다면 해당 step의 stepImageIndex에 이미지 번호를 기재해주세요.
            이미지 번호는 레시피가 여러 개여도 전체 사진 기준 번호(1~%d)를 그대로 사용하세요.

            다음 JSON 배열 형식으로만 응답하세요 (다른 텍스트 없이):
            [
              {
                "title": "레시피 제목",
                "description": "간단한 설명 (2~3문장)",
                "ageGroup": "BABY_FOOD 또는 ADULT_FOOD 중 하나",
                "category": "PORRIDGE 또는 SOUP 또는 SIDE_DISH 또는 FINGER_FOOD 또는 SNACK 또는 DRINK 중 하나",
                "cookingTime": 조리시간(분, 숫자 또는 null),
                "servings": 인분수(숫자 또는 null),
                "ingredients": [{"name": "재료명", "amount": "양", "unit": "단위"}],
                "steps": [{"order": 1, "description": "조리 단계 설명", "stepImageIndex": 이미지번호또는null}],
                "tags": ["태그1", "태그2"],
                "finishedImageIndex": 완성본이미지번호또는null
              }
            ]

            연령 그룹: 이유식(영아용, 18개월 미만 기준)→BABY_FOOD, 어른 음식(가족 식사 등 일반 요리)→ADULT_FOOD
            카테고리: 죽→PORRIDGE, 국찌개→SOUP, 반찬→SIDE_DISH, 핑거푸드→FINGER_FOOD, 간식→SNACK, 음료→DRINK
            반드시 JSON 배열로만 응답하세요 (배열을 감싸지 않은 단일 객체 금지).
            """.formatted(count, count);

        blocks.add(ContentBlock.text(prompt));
        return callClaude(List.of(new ClaudeMessage("user", blocks)));
    }

    // ── 공통 Claude HTTP 호출 ──────────────────────────────────────────────────

    private String callClaude(List<ClaudeMessage> messages) {
        try {
            ClaudeRequestBody body = new ClaudeRequestBody(model, 8192, messages);
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

    private List<RecipeExtractResponse> parseResult(String jsonText) {
        try {
            List<JsonNode> nodes = extractJsonNodes(jsonText);
            List<RecipeExtractResponse> results = new ArrayList<>();
            for (JsonNode node : nodes) {
                results.add(objectMapper.treeToValue(node, RecipeExtractResponse.class));
            }
            return results;
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.error("레시피 JSON 파싱 실패: {}", jsonText, e);
            throw BabyRecipeException.badRequest("레시피 정보를 추출할 수 없습니다.");
        }
    }

    private List<RecipeExtractResponse> parseVisionResult(String jsonText, List<String> savedUrls) {
        try {
            List<JsonNode> nodes = extractJsonNodes(jsonText);
            List<RecipeExtractResponse> results = new ArrayList<>();

            for (JsonNode node : nodes) {
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

                results.add(response);
            }

            return results;
        } catch (BabyRecipeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vision 레시피 JSON 파싱 실패: {}", jsonText, e);
            throw BabyRecipeException.badRequest("이미지에서 레시피 정보를 추출할 수 없습니다.");
        }
    }

    private List<JsonNode> extractJsonNodes(String jsonText) throws Exception {
        String cleaned = jsonText.trim();

        Matcher fenceMatcher = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(cleaned);
        if (fenceMatcher.find()) {
            cleaned = fenceMatcher.group(1).trim();
        } else {
            int braceStart = cleaned.indexOf('{');
            int bracketStart = cleaned.indexOf('[');
            int start;
            char endChar;
            if (bracketStart != -1 && (braceStart == -1 || bracketStart < braceStart)) {
                start = bracketStart;
                endChar = ']';
            } else {
                start = braceStart;
                endChar = '}';
            }
            int end = cleaned.lastIndexOf(endChar);
            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
        }

        if (cleaned.equals("null") || cleaned.isBlank()) {
            throw BabyRecipeException.badRequest("레시피 정보를 찾을 수 없습니다.");
        }

        cleaned = repairJson(cleaned);

        JsonNode node = objectMapper.readTree(cleaned);
        List<JsonNode> nodes = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(nodes::add);
        } else if (node.isObject()) {
            nodes.add(node);
        }
        if (nodes.isEmpty()) {
            throw BabyRecipeException.badRequest("레시피 정보를 찾을 수 없습니다.");
        }
        return nodes;
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
