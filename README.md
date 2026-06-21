# BabyRecipe SNS

아기/유아식 레시피를 공유하고 저장하는 SNS 플랫폼입니다.  
부모들이 월령별 레시피를 작성·검색하고, 팔로우·좋아요·북마크로 소통할 수 있습니다.

---

## 기술 스택

### Backend
| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (Access 1h / Refresh 7d) |
| ORM | Spring Data JPA / Hibernate |
| Database | MySQL 8 |
| HTML Parsing | Jsoup 1.17.2 |
| Build Tool | Gradle |

### Frontend
| 항목 | 내용 |
|------|------|
| Framework | React 18.3 |
| Build Tool | Vite 5.3 |
| Routing | React Router DOM 6 |
| HTTP Client | Axios 1.7 |

### Infrastructure
| 항목 | 내용 |
|------|------|
| Server | AWS EC2 (Amazon Linux 2023) |
| Database | AWS RDS (MySQL 8) |
| Region | ap-northeast-2 (서울) |

---

## 주요 기능

- **회원 관리** — 이메일/비밀번호 회원가입, JWT 로그인, 프로필 수정
- **레시피 CRUD** — 제목·설명·월령·카테고리·재료·조리순서·이미지 포함 레시피 작성/수정/삭제
- **레시피 검색** — 제목·재료·태그 키워드 검색, 월령·카테고리 필터, 최신순/인기순 정렬
- **URL 레시피 추출** — 외부 URL에서 레시피 정보 자동 파싱 (Jsoup)
- **소셜 기능** — 좋아요, 북마크, 댓글(대댓글), 팔로우/언팔로우
- **팔로잉 피드** — 팔로우한 사용자의 최신 레시피 모아보기

### 월령 구분

| 코드 | 설명 |
|------|------|
| MONTH_4_6 | 4~6개월 (이유식 초기) |
| MONTH_7_9 | 7~9개월 (이유식 중기) |
| MONTH_10_12 | 10~12개월 (이유식 후기) |
| MONTH_12_18 | 12~18개월 (완료기) |
| MONTH_18_PLUS | 18개월 이상 (유아식) |

### 카테고리

`PORRIDGE(죽)` · `SOUP(국/찌개)` · `SIDE_DISH(반찬)` · `FINGER_FOOD(핑거푸드)` · `SNACK(간식)` · `DRINK(음료)`

---

## 프로젝트 구조

```
baby_recipe/
├── src/main/java/com/babyrecipe/
│   ├── config/          # SecurityConfig, JwtProvider, JwtAuthenticationFilter, WebMvcConfig
│   ├── controller/      # Auth, Recipe, User, Comment, Feed, ImageUpload, RecipeExtract, Spa
│   ├── domain/          # User, Recipe, Ingredient, RecipeIngredient, RecipeStep,
│   │                    # Tag, Comment, Like, Bookmark, Follow, RefreshToken
│   ├── dto/
│   │   ├── request/     # RegisterRequest, LoginRequest, RecipeRequest, ...
│   │   └── response/    # ApiResponse<T>, UserResponse, RecipeResponse, ...
│   ├── repository/      # Spring Data JPA repositories
│   ├── service/         # AuthService, RecipeService, UserService, CommentService,
│   │                    # ImageStorageService, RecipeExtractService
│   └── exception/       # GlobalExceptionHandler, Custom Exceptions
├── src/main/resources/
│   ├── application.yml          # 공통 설정 (기본 프로파일: local)
│   ├── application-local.yml    # 로컬 DB
│   └── application-prod.yml     # AWS RDS (환경변수 기반)
└── frontend/
    └── src/
        ├── pages/       # Home, Login, Register, Feed, RecipeDetail, RecipeForm,
        │                # UserProfile, EditProfile
        ├── components/  # Navbar, RecipeCard, PrivateRoute
        ├── api/         # Axios 인스턴스 및 API 함수
        └── contexts/    # 인증 Context
```

---

## API 엔드포인트

### 인증
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/register` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/logout` | 로그아웃 |
| POST | `/api/auth/refresh` | Access Token 재발급 |

### 사용자
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/users/{id}` | 프로필 조회 |
| PUT | `/api/users/me` | 내 프로필 수정 |
| POST | `/api/users/{id}/follow` | 팔로우 |
| DELETE | `/api/users/{id}/follow` | 언팔로우 |
| GET | `/api/users/{id}/followers` | 팔로워 목록 |
| GET | `/api/users/{id}/following` | 팔로잉 목록 |
| GET | `/api/users/me/bookmarks` | 북마크 목록 |

### 레시피
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/recipes` | 목록 조회 (페이지네이션, 필터) |
| POST | `/api/recipes` | 레시피 작성 |
| GET | `/api/recipes/{id}` | 레시피 상세 |
| PUT | `/api/recipes/{id}` | 레시피 수정 |
| DELETE | `/api/recipes/{id}` | 레시피 삭제 |
| POST | `/api/recipes/{id}/like` | 좋아요 |
| DELETE | `/api/recipes/{id}/like` | 좋아요 취소 |
| POST | `/api/recipes/{id}/bookmark` | 북마크 |
| DELETE | `/api/recipes/{id}/bookmark` | 북마크 취소 |
| GET | `/api/recipes/{id}/comments` | 댓글 목록 |
| POST | `/api/recipes/{id}/comments` | 댓글 작성 |
| DELETE | `/api/recipes/{id}/comments/{cid}` | 댓글 삭제 |
| POST | `/api/recipes/extract` | URL에서 레시피 추출 |

### 기타
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/feed` | 팔로잉 피드 |
| POST | `/api/image/upload` | 이미지 업로드 |

---

## 로컬 실행 방법

### 사전 요구사항

- Java 17+
- MySQL 8 (로컬)
- Node.js 18+

### 백엔드 실행

```bash
# 환경변수 설정 (선택, 기본값 있음)
export DB_USERNAME=root
export DB_PASSWORD=password

# 빌드 및 실행 (local 프로파일 자동 적용)
./gradlew bootRun
```

로컬 DB 설정은 `src/main/resources/application-local.yml`에서 수정합니다.

### 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

### 프론트엔드 빌드 (Spring Boot에 포함)

```bash
cd frontend && npm run build
# 빌드 결과물이 src/main/resources/static/ 에 복사됨
cd .. && ./gradlew bootRun
```

---

## AWS 배포 방법

### 환경변수 (EC2 `~/.bashrc`)

```bash
export DB_HOST=<RDS 엔드포인트>
export DB_USERNAME=admin
export DB_PASSWORD=<비밀번호>
export JWT_SECRET=<256비트 이상의 시크릿 키>
```

### 빌드 및 배포

```bash
# 로컬에서 빌드
./gradlew clean build -x test

# EC2로 전송
scp -i babyrecipe-key.pem build/libs/baby-recipe-1.0.0.jar ec2-user@<EC2_IP>:~/app.jar

# EC2에서 실행
nohup java -jar app.jar --spring.profiles.active=prod > app.log 2>&1 &
```

### 앱 관리

```bash
# 로그 확인
tail -f app.log

# 프로세스 확인
ps -ef | grep java

# 앱 종료
kill $(ps -ef | grep java | grep -v grep | awk '{print $2}')
```

---

## 계층 구조

```
Controller (HTTP 요청/응답)
    ↕  DTO (Request / Response)
Service (비즈니스 로직, @Transactional)
    ↕  Domain Entity
Repository (Spring Data JPA)
    ↕
MySQL Database
```

표준 응답 포맷: `ApiResponse<T>` (모든 API 응답에 일관되게 사용)

---

## 향후 계획

- [ ] GitHub Actions를 이용한 자동 배포 (CI/CD)
- [ ] 도메인 연결 + HTTPS (Route 53 / Let's Encrypt)
- [ ] systemd 서비스 등록 (EC2 재부팅 시 자동 시작)
- [ ] Elastic IP 할당 (퍼블릭 IP 고정)
- [ ] 알림 기능 (좋아요·댓글·팔로우 이벤트)
