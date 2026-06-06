# 프로젝트 시작 가이드

## 사전 준비

- Java 17 이상
- Node.js 18 이상
- MySQL 8.0 이상

---

## 1. 데이터베이스 설정

MySQL에서 데이터베이스를 생성합니다.

```sql
CREATE DATABASE baby_recipe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

기본 접속 정보 (환경변수로 재정의 가능):
- Host: `localhost:3306`
- DB: `baby_recipe`
- Username: `root` (`DB_USERNAME` 환경변수로 변경)
- Password: `password` (`DB_PASSWORD` 환경변수로 변경)

---

## 2. 환경변수 설정 (선택)

기본값을 변경하려면 아래 환경변수를 설정합니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `DB_USERNAME` | `root` | DB 사용자명 |
| `DB_PASSWORD` | `password` | DB 비밀번호 |
| `JWT_SECRET` | (내장값) | JWT 서명 키 |
| `UPLOAD_DIR` | `uploads` | 파일 업로드 경로 |
| `ANTHROPIC_API_KEY` | (없음) | Claude AI API 키 |

---

## 3. 백엔드 실행 (Spring Boot)

프로젝트 루트(`/home/jy_sm/test/baby_recipe`)에서 실행합니다.

```bash
./gradlew bootRun
```

환경변수와 함께 실행할 경우:

```bash
DB_USERNAME=root DB_PASSWORD=1234 ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun
```

- 실행 주소: `http://localhost:8080`
- 테이블은 JPA `ddl-auto: update`로 자동 생성됩니다.

---

## 4. 프론트엔드 실행 (React + Vite)

```bash
cd frontend
npm install    # 최초 1회만
npm run dev
```

- 실행 주소: `http://localhost:3000`

---

## 5. 전체 실행 순서 요약

```
1. MySQL 실행 및 baby_recipe DB 생성
2. ./gradlew bootRun          (백엔드)
3. cd frontend && npm run dev  (프론트엔드)
```
