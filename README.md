# reciplog

**recipe + log** — 레시피를 기록하고 공유하는 서비스입니다.

직접 만든 레시피를 재료·조리순서·사진과 함께 남기고, 외부 웹페이지 URL만 붙여넣으면 AI가 레시피를 자동으로 정리해 줍니다.
팔로우·좋아요·북마크·댓글로 다른 사용자와 소통하고, 실시간 1:1 채팅으로 대화할 수 있습니다.

> 이유식 레시피 기록 서비스에서 출발했습니다. 현재 데이터 모델에는 월령 구분이 남아 있어 이유식 도메인에 최적화돼 있고, 범용 레시피로의 확장은 [향후 계획](#향후-계획)에 있습니다.

백엔드 학습을 목적으로 만든 포트폴리오 프로젝트입니다.

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
| Realtime | Spring WebSocket + STOMP |
| AI | Anthropic Claude API (claude-haiku-4-5) |
| HTML Parsing | Jsoup 1.17.2 |
| Build Tool | Gradle |

### Frontend
| 항목 | 내용 |
|------|------|
| Framework | React 18.3 |
| Build Tool | Vite 5.3 |
| Routing | React Router DOM 6 |
| HTTP Client | Axios 1.7 |
| Realtime | @stomp/stompjs |

### Infrastructure
| 항목 | 내용 |
|------|------|
| Server | AWS EC2 (Amazon Linux 2023) |
| Database | AWS RDS (MySQL 8) |
| Region | ap-northeast-2 (서울) |
| CI | GitHub Actions + SonarCloud 정적 분석 |
| CD | GitHub Actions (main 푸시 시 EC2 자동 배포) |
| Process | systemd (`deploy/babyrecipe.service`) |

---

## 주요 기능

- **회원 관리** — 이메일/비밀번호 회원가입, JWT 로그인, 프로필 수정
- **로그인 상태 유지** — 체크 시 자동 로그인(7일, 접속할 때마다 갱신), 미체크 시 브라우저 종료와 함께 로그아웃
- **기기별 동시 로그인** — 폰과 PC에서 각각 로그인해도 서로의 세션을 끊지 않으며, 로그아웃은 해당 기기만 종료
- **레시피 CRUD** — 제목·설명·월령·카테고리·재료·조리순서·이미지 포함 레시피 작성/수정/삭제
- **레시피 검색** — 제목·재료·태그 키워드 검색, 월령·카테고리 필터, 최신순/인기순 정렬
- **AI 레시피 추출** — 외부 URL을 Jsoup으로 파싱한 뒤 Claude가 재료·조리순서를 구조화. 한 페이지에 여러 레시피가 있으면 각각 추출
- **소셜 기능** — 좋아요, 북마크, 댓글(대댓글), 팔로우/언팔로우
- **팔로잉 피드** — 팔로우한 사용자의 최신 레시피 모아보기
- **실시간 1:1 채팅** — WebSocket/STOMP 기반, JWT로 연결 인증

### 월령 구분

이유식 도메인에서 출발한 분류입니다. 범용 확장 시 선택 항목으로 전환할 예정입니다.

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
reciplog/
├── src/main/java/com/babyrecipe/
│   ├── config/          # SecurityConfig, JwtProvider, JwtAuthenticationFilter,
│   │                    # WebSocketConfig, StompChannelInterceptor,
│   │                    # CustomUserDetailsService, WebMvcConfig
│   ├── controller/      # Auth, Recipe, User, Comment, Feed, ImageUpload,
│   │                    # RecipeExtract, Chat, ChatStomp, Spa
│   ├── domain/          # User, Recipe, Ingredient, RecipeIngredient, RecipeStep,
│   │                    # Tag, Comment, Like, Bookmark, Follow, RefreshToken,
│   │                    # ChatRoom, ChatParticipant, ChatMessage
│   ├── dto/
│   │   ├── request/     # RegisterRequest, LoginRequest, RecipeRequest, ...
│   │   └── response/    # ApiResponse<T>, UserResponse, RecipeResponse, ...
│   ├── repository/      # Spring Data JPA repositories
│   ├── service/         # AuthService, RecipeService, UserService, CommentService,
│   │                    # ChatService, ImageStorageService, RecipeExtractService
│   └── exception/       # GlobalExceptionHandler, Custom Exceptions
├── src/main/resources/
│   ├── application.yml          # 공통 설정 (기본 프로파일: local)
│   └── application-prod.yml     # AWS RDS (환경변수 기반)
├── deploy/
│   ├── babyrecipe.service       # systemd 유닛
│   └── setup-systemd.sh
├── .github/workflows/
│   ├── cd.yml                   # main 푸시 시 EC2 배포
│   └── sonarcloud-analyze.yml   # PR 정적 분석
└── frontend/
    └── src/
        ├── pages/       # Home, Login, Register, Feed, RecipeDetail, RecipeForm,
        │                # UserProfile, EditProfile, ChatRoomList, ChatRoom
        ├── components/  # Navbar, RecipeCard, PrivateRoute
        ├── api/         # axios.js(인터셉터), stomp.js(WebSocket), tokenStorage.js
        ├── contexts/    # 인증 Context
        └── utils/       # sourceLink.js
```

---

## 인증 구조

| 토큰 | 만료 | 저장 위치 |
|------|------|-----------|
| Access Token | 1시간 | 로그인 상태 유지 체크 시 `localStorage`, 아니면 `sessionStorage` |
| Refresh Token | 7일 (갱신할 때마다 재설정) | 동일 |

- Access Token 만료로 401이 나면 Axios 인터셉터가 자동으로 재발급하고 원래 요청을 재시도합니다. 동시 요청이 여러 개여도 재발급은 한 번만 수행합니다.
- Refresh Token은 사용할 때마다 새로 발급(rotation)되며, DB에 저장된 이전 토큰은 폐기됩니다.
- 각 토큰에는 `jti`(UUID)가 들어 있어, 같은 초에 발급되어도 항상 서로 다른 토큰이 됩니다.

---

## API 엔드포인트

### 인증
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/register` | 회원가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/logout` | 로그아웃 (`refreshToken` 지정 시 해당 세션만) |
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
| POST | `/api/recipes/extract` | URL에서 레시피 추출 (AI) |

### 채팅
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/chat/rooms` | 참여 중인 채팅방 목록 |
| POST | `/api/chat/rooms` | 1:1 채팅방 생성 (있으면 기존 방 반환) |
| GET | `/api/chat/rooms/{roomId}/messages` | 메시지 이력 |

**WebSocket (STOMP)** — 연결 엔드포인트 `/ws`, `Authorization: Bearer <accessToken>` 헤더로 인증

| 방향 | 목적지 | 설명 |
|------|--------|------|
| 발신 | `/app/chat.send` | 메시지 전송 |
| 구독 | `/topic/chat.{roomId}` | 해당 방의 메시지 수신 |
| 구독 | `/user/queue/errors` | 오류 수신 |

### 기타
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/feed` | 팔로잉 피드 |
| POST | `/api/image/upload` | 이미지 업로드 |

---

## 로컬 실행 방법

### 사전 요구사항

- Java 17+
- MySQL 8
- Node.js 18+

### 1. 데이터베이스 생성

```sql
CREATE DATABASE baby_recipe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

테이블은 JPA `ddl-auto: update`로 자동 생성됩니다.

### 2. 백엔드 실행

기본 프로파일은 `local`이지만 **`application.yml`에는 datasource 설정이 없습니다.** (datasource는 `application-prod.yml`에만 있습니다.) 따라서 접속 정보를 직접 넘겨야 합니다.

```bash
./gradlew bootRun --args='\
  --spring.datasource.url=jdbc:mysql://localhost:3306/baby_recipe?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8 \
  --spring.datasource.username=root \
  --spring.datasource.password=<비밀번호> \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver'
```

AI 레시피 추출을 쓰려면 `ANTHROPIC_API_KEY` 환경변수도 필요합니다.

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

**개발 중에는 `http://localhost:3000`으로 접속하세요.** Vite 개발 서버가 `/api`와 `/ws`를 8080으로 프록시합니다.
`http://localhost:8080`으로 직접 접속하면 `/login` 같은 SPA 경로가 `index.html`로 포워딩되지 않아 동작하지 않습니다.

### 프론트엔드 빌드 (Spring Boot에 포함)

```bash
cd frontend && npm run build   # 결과물이 src/main/resources/static/ 에 생성됨
```

---

## 배포

`main` 브랜치에 푸시하면 GitHub Actions(`cd.yml`)가 프론트엔드 빌드 → jar 빌드 → EC2 전송 → systemd 재시작까지 자동으로 수행합니다.

### EC2 환경변수 (`/home/ec2-user/babyrecipe.env`)

systemd 유닛(`deploy/babyrecipe.service`)이 `EnvironmentFile`로 읽는 파일입니다.
systemd는 로그인 셸을 거치지 않으므로 `~/.bashrc`에 넣으면 앱에 전달되지 않습니다.

```bash
DB_HOST=<RDS 엔드포인트>
DB_USERNAME=admin
DB_PASSWORD=<비밀번호>
JWT_SECRET=<256비트 이상의 시크릿 키>
ANTHROPIC_API_KEY=<API 키>
```

> `EnvironmentFile`은 `export` 없는 `KEY=value` 형식을 씁니다. 실제 서버 파일의 키 목록과 형식은 아직 대조하지 못했습니다.

### 수동 배포 (필요 시)

```bash
./gradlew clean build -x test
scp -i <키>.pem build/libs/reciplog-1.0.0.jar ec2-user@<EC2_IP>:~/app.jar
ssh ec2-user@<EC2_IP> 'sudo systemctl restart babyrecipe'
```

### 로그 확인

```bash
sudo journalctl -u babyrecipe -f
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

- [ ] **범용 레시피로 확장** — 월령 구분을 필수에서 선택으로 전환하고, 이유식 외 카테고리 추가
- [ ] 도메인 연결 + HTTPS (Route 53 / Let's Encrypt)
- [ ] Elastic IP 할당 (퍼블릭 IP 고정)
- [ ] 알림 기능 (좋아요·댓글·팔로우 이벤트)
- [ ] Refresh Token 절대 만료 상한 추가 (현재는 갱신할 때마다 연장되어 상한이 없음)
- [ ] Refresh Token을 httpOnly 쿠키로 이전 (현재 `localStorage` 저장이라 XSS 시 탈취 위험)
