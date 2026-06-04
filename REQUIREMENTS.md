# 아기 유아식 레시피 SNS 웹앱 요구 설계서

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | BabyRecipe SNS |
| 목적 | 부모들이 아기/유아식 레시피를 공유하고 저장하는 SNS 플랫폼 |
| 기술 스택 | Java 17, Spring Boot 3.2, Spring MVC, Spring Security, JPA/Hibernate, MySQL 8 |

---

## 2. 기능 요구사항 (Functional Requirements)

### 2.1 회원 관리
| ID | 기능 | 설명 |
|----|------|------|
| AUTH-01 | 회원가입 | 이메일/비밀번호/닉네임으로 가입, 이메일 중복 검사 |
| AUTH-02 | 로그인 | JWT 토큰 발급 (Access Token 1h, Refresh Token 7d) |
| AUTH-03 | 로그아웃 | Refresh Token 무효화 |
| AUTH-04 | 토큰 갱신 | Refresh Token으로 Access Token 재발급 |
| USER-01 | 프로필 조회 | 닉네임, 프로필 사진, 소개글, 팔로워/팔로잉 수, 레시피 수 |
| USER-02 | 프로필 수정 | 닉네임, 프로필 사진, 소개글 변경 |
| USER-03 | 팔로우/언팔로우 | 다른 사용자 팔로우/취소 |
| USER-04 | 팔로워/팔로잉 목록 | 팔로워·팔로잉 사용자 목록 조회 |

### 2.2 레시피 관리
| ID | 기능 | 설명 |
|----|------|------|
| RECIPE-01 | 레시피 작성 | 제목, 설명, 월령 구분, 카테고리, 재료 목록, 조리 순서, 이미지 업로드 |
| RECIPE-02 | 레시피 조회 | 상세 정보, 조회수 증가, 댓글/좋아요 수 |
| RECIPE-03 | 레시피 수정 | 작성자 본인만 가능 |
| RECIPE-04 | 레시피 삭제 | 작성자 본인 또는 관리자만 가능 |
| RECIPE-05 | 레시피 검색 | 제목/재료/태그로 검색, 월령·카테고리 필터 |
| RECIPE-06 | 레시피 목록 | 최신순/인기순/팔로잉순 정렬, 페이지네이션 |

### 2.3 소셜 기능
| ID | 기능 | 설명 |
|----|------|------|
| SOCIAL-01 | 좋아요 | 레시피 좋아요/취소 (중복 방지) |
| SOCIAL-02 | 북마크 | 레시피 저장/취소, 북마크 목록 조회 |
| SOCIAL-03 | 댓글 | 댓글 작성/수정/삭제, 대댓글 지원 |
| SOCIAL-04 | 피드 | 팔로잉 사용자의 최신 레시피 피드 |
| SOCIAL-05 | 알림 | 좋아요·댓글·팔로우 이벤트 알림 (추후 확장) |

### 2.4 월령 구분 (AgeGroup Enum)
| 코드 | 설명 |
|------|------|
| MONTH_4_6 | 4~6개월 (이유식 초기) |
| MONTH_7_9 | 7~9개월 (이유식 중기) |
| MONTH_10_12 | 10~12개월 (이유식 후기) |
| MONTH_12_18 | 12~18개월 (완료기) |
| MONTH_18_PLUS | 18개월 이상 (유아식) |

### 2.5 카테고리 (Category Enum)
`PORRIDGE(죽)`, `SOUP(국/찌개)`, `SIDE_DISH(반찬)`, `FINGER_FOOD(핑거푸드)`, `SNACK(간식)`, `DRINK(음료)`

---

## 3. 비기능 요구사항 (Non-Functional Requirements)

| 항목 | 요건 |
|------|------|
| 보안 | JWT 기반 인증, BCrypt 비밀번호 암호화, XSS/CSRF 방어 |
| 성능 | 목록 조회 응답 < 300ms, 인덱스 설정 (recipe.created_at, user.email) |
| 확장성 | RESTful API 설계, 계층 분리 (Controller → Service → Repository) |
| 유지보수 | 전역 예외 처리, 표준 응답 포맷, Validation |

---

## 4. 도메인 모델 (ERD)

```
users
  id (PK), email (UNIQUE), password, nickname, profile_image, bio,
  role (USER/ADMIN), created_at, updated_at

recipes
  id (PK), title, description, cooking_time, servings, image_url,
  view_count, age_group, category, user_id (FK→users), created_at, updated_at

ingredients
  id (PK), name (UNIQUE)

recipe_ingredients
  id (PK), recipe_id (FK), ingredient_id (FK), amount, unit

recipe_steps
  id (PK), recipe_id (FK), step_order, description, image_url

tags
  id (PK), name (UNIQUE)

recipe_tags
  recipe_id (FK), tag_id (FK)  [PK: composite]

comments
  id (PK), recipe_id (FK), user_id (FK), parent_id (FK→comments, nullable),
  content, created_at, updated_at

likes
  id (PK), recipe_id (FK), user_id (FK), created_at
  [UNIQUE: recipe_id + user_id]

bookmarks
  id (PK), recipe_id (FK), user_id (FK), created_at
  [UNIQUE: recipe_id + user_id]

follows
  id (PK), follower_id (FK→users), following_id (FK→users), created_at
  [UNIQUE: follower_id + following_id]

refresh_tokens
  id (PK), user_id (FK), token (UNIQUE), expires_at, created_at
```

---

## 5. API 설계

### 인증
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/auth/register | 회원가입 |
| POST | /api/auth/login | 로그인 |
| POST | /api/auth/logout | 로그아웃 |
| POST | /api/auth/refresh | 토큰 갱신 |

### 사용자
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/users/{id} | 프로필 조회 |
| PUT | /api/users/me | 내 프로필 수정 |
| POST | /api/users/{id}/follow | 팔로우 |
| DELETE | /api/users/{id}/follow | 언팔로우 |
| GET | /api/users/{id}/followers | 팔로워 목록 |
| GET | /api/users/{id}/following | 팔로잉 목록 |
| GET | /api/users/me/bookmarks | 북마크 목록 |

### 레시피
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/recipes | 목록 조회 (페이지네이션, 필터) |
| POST | /api/recipes | 레시피 작성 |
| GET | /api/recipes/{id} | 레시피 상세 |
| PUT | /api/recipes/{id} | 레시피 수정 |
| DELETE | /api/recipes/{id} | 레시피 삭제 |
| POST | /api/recipes/{id}/like | 좋아요 |
| DELETE | /api/recipes/{id}/like | 좋아요 취소 |
| POST | /api/recipes/{id}/bookmark | 북마크 |
| DELETE | /api/recipes/{id}/bookmark | 북마크 취소 |
| GET | /api/recipes/{id}/comments | 댓글 목록 |
| POST | /api/recipes/{id}/comments | 댓글 작성 |
| DELETE | /api/recipes/{id}/comments/{cid} | 댓글 삭제 |
| GET | /api/feed | 팔로잉 피드 |

---

## 6. 계층 구조

```
Controller (HTTP 요청/응답)
    ↕  DTO (Request/Response)
Service (비즈니스 로직, @Transactional)
    ↕  Domain Entity
Repository (Spring Data JPA)
    ↕
MySQL Database
```

---

## 7. 디렉터리 구조

```
src/main/java/com/babyrecipe/
├── BabyRecipeApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── JwtProvider.java
│   └── WebMvcConfig.java
├── controller/
│   ├── AuthController.java
│   ├── RecipeController.java
│   ├── UserController.java
│   ├── CommentController.java
│   └── FeedController.java
├── domain/
│   ├── User.java
│   ├── Recipe.java
│   ├── Ingredient.java
│   ├── RecipeIngredient.java
│   ├── RecipeStep.java
│   ├── Tag.java
│   ├── Comment.java
│   ├── Like.java
│   ├── Bookmark.java
│   ├── Follow.java
│   └── RefreshToken.java
├── dto/
│   ├── request/  (RegisterRequest, LoginRequest, RecipeRequest, ...)
│   └── response/ (ApiResponse, UserResponse, RecipeResponse, ...)
├── repository/
│   └── (각 도메인 Repository)
├── service/
│   ├── AuthService.java
│   ├── RecipeService.java
│   ├── UserService.java
│   └── CommentService.java
└── exception/
    ├── GlobalExceptionHandler.java
    └── (Custom Exception classes)
```
