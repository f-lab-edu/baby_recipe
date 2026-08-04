# ageGroup 라벨 통합 설계안 (5단계 → 2단계)

- 작성: 민준 (PM·아키텍트)
- 상태: **승인 대기** (쭌 승인 전 코드 수정 금지)
- 대상: `/workspace/reciplog`
- 목표: 기존 연령대 5단계(`MONTH_4_6`~`MONTH_18_PLUS`)를 **`이유식` / `어른 음식`** 2단계로 통합

---

## 1. 배경 / 문제

`Recipe.AgeGroup` enum이 5단계로 세분화되어 있으나, 제품 방향이 "이유식 vs 어른(가족) 음식" 2분류로 단순화됨.
제약이 핵심이다:

- `ageGroup`은 `@Enumerated(EnumType.STRING)` → DB `recipes.age_group` 컬럼에 **문자열 그대로** 저장 중
  (`"MONTH_4_6"`, `"MONTH_7_9"`, …). 즉 기존 데이터가 enum "이름"에 물려 있다.
- **flyway 등 마이그레이션 도구 없음** → 스키마/데이터 변경을 자동 관리할 수단이 없다.

따라서 "enum 이름을 바꾸는 것"은 곧 "이미 저장된 문자열과 코드가 어긋나는 것"이며,
기존 행을 어떻게 처리할지가 설계의 중심이 된다.

---

## 2. 현재 구조 — `ageGroup` 사용처 전수 조사

| # | 파일 | 사용 형태 | 통합 시 영향 |
|---|------|-----------|--------------|
| 1 | `domain/Recipe.java` | `enum AgeGroup` 5값 + `@Enumerated(STRING)` 필드 | **핵심** |
| 2 | `dto/request/RecipeRequest.java` | `@NotNull Recipe.AgeGroup ageGroup` (요청 바인딩) | enum 값 축소 영향 |
| 3 | `dto/response/RecipeResponse.java` | `ageGroup=name()`, `ageGroupLabel=label` 출력 | 라벨 노출 지점 |
| 4 | `controller/RecipeController.java` | `@RequestParam Recipe.AgeGroup ageGroup` (필터) | 잘못된 값 시 400 |
| 5 | `repository/RecipeRepository.java` | JPQL `r.ageGroup = :ageGroup` 필터 | 필터 로직 |
| 6 | `service/RecipeService.java` | 위 파라미터를 그대로 전달 | 시그니처 유지 가능 |
| 7 | `service/RecipeExtractService.java` | **AI 프롬프트 2곳**이 `MONTH_*` 5값을 문자열로 지시 → 응답 파싱 후 프론트로 전달 | 프롬프트 수정 필요 |
| 8 | `dto/response/RecipeExtractResponse.java` | `String ageGroup` (검증 없음, AI 출력 그대로) | 값만 바뀜 |
| 9 | `frontend/.../Home.jsx` | `AGE_GROUPS` 하드코딩(필터, '전체 연령' 포함) | 목록 교체 |
| 10 | `frontend/.../RecipeForm.jsx` | `AGE_GROUPS` 하드코딩 + `blankForm()`/`useEffect` 기본값 `'MONTH_4_6'` | 목록·기본값 교체 |
| 11 | `frontend/.../RecipeCard.jsx` | `recipe.ageGroupLabel` 표시 | 코드 변경 불필요(라벨은 백엔드가 줌) |
| 12 | `frontend/.../RecipeDetail.jsx` | `recipe.ageGroupLabel` 표시 | 코드 변경 불필요 |

핵심 관찰:
- **표시 라벨은 전부 백엔드 `ageGroupLabel`에서 내려온다** → 카드/상세 화면(11,12)은 백엔드만 바꾸면 자동 반영.
- 프론트의 하드코딩은 **필터 버튼(9)** 과 **작성 폼 select + 기본값(10)** 두 군데뿐.
- `RecipeExtractResponse.ageGroup`은 String이고 검증이 없다 → AI가 옛 값을 뱉으면 그대로 흘러 최종 저장 시 enum 변환 지점(2)에서 걸린다. **프롬프트(7) 반드시 동반 수정 필요.**

---

## 3. 【결정사항 1】 기존 `MONTH_*` → 이유식 / 어른 음식 매핑

권장(안 A):

| 기존 값 | 라벨 | → 통합 그룹 |
|---------|------|------------|
| `MONTH_4_6` | 4~6개월 | **이유식** |
| `MONTH_7_9` | 7~9개월 | **이유식** |
| `MONTH_10_12` | 10~12개월 | **이유식** |
| `MONTH_12_18` | 12~18개월 | **이유식** |
| `MONTH_18_PLUS` | 18개월 이상 | **어른 음식** |

근거: 이유식은 통상 생후 4개월~완료기(12~18개월)까지 진행되고, 18개월 이후부터
유아식/가족식(어른 음식에 가까운 형태)으로 넘어간다. 따라서 완료기까지는 이유식, 그 이후를
어른 음식으로 두는 것이 도메인상 가장 자연스럽다.

**쭌 확인 필요 지점 — `MONTH_12_18`의 경계.**
"완료기(12~18개월)를 어른 음식 쪽으로 볼지"는 제품 판단 영역이다. 대안 B는 아래와 같다:

- 안 B: `MONTH_4_6/7_9/10_12` → 이유식, `MONTH_12_18/18_PLUS` → 어른 음식

> ⚠️ 이 매핑은 결정사항 2(교체/유지)와 무관하게 **기존 데이터 변환·필터 그룹핑에 그대로 사용**되므로,
> 승인 시 A/B 중 하나를 반드시 확정해 주세요. (기본 권장: **안 A**)

---

## 4. 【결정사항 2】 enum 직접 교체 vs 2그룹 별도 개념 추가

두 가지 전략을 비교한다.

### 전략 1 — enum 직접 교체 (권장)

`AgeGroup`을 2값으로 재정의:

```java
public enum AgeGroup {
    BABY_FOOD("이유식"),
    ADULT_FOOD("어른 음식");
    ...
}
```

- DB에는 앞으로 `"BABY_FOOD"` / `"ADULT_FOOD"` 문자열이 저장된다.
- **기존 행은 1회성 데이터 변환 필요** (5절 참조). flyway가 없으므로 **네이티브 SQL `UPDATE` 1건**으로 수행.
- 네이티브 SQL은 enum으로 역직렬화하지 않고 문자열만 바꾸므로, 코드 배포와 무관하게 안전하게 실행 가능.

| 장점 | 단점 |
|------|------|
| 데이터 모델이 제품 개념과 1:1로 깔끔 | 기존 데이터 1회 변환 필요 |
| AI 프롬프트를 2값으로 축소 → 오답률↓ | 변환 전 옛 값 잔존 시 조회 오류(배포 순서로 통제) |
| 필터/저장 모두 값 그대로 사용 (그룹핑 계층 불필요) | 롤백 시 역변환 SQL 필요 |
| 레거시 5값 부채 제거 | |

### 전략 2 — enum 유지 + 2그룹 파생 계층

`AgeGroup` 5값을 그대로 두고, `이유식/어른 음식`을 파생 개념(예: `FoodStage`)으로 얹는다.
표시 라벨과 필터를 그룹 단위로 변환.

| 장점 | 단점 |
|------|------|
| **데이터 변환 불필요**(기존 문자열 그대로 유효) | 새 레시피 저장 시 "2그룹 중 하나 → 5값 중 무엇으로 저장?"이라는 인위적 매핑 필요 |
| 배포 리스크 최소 | 표현(2)과 저장(5)이 영구히 이원화 → 지속적 혼란·부채 |
| | 필터마다 그룹→`IN(...)` 변환 로직 상시 유지 |
| | 프롬프트/폼 단순화 이점 못 누림 |

### 권장: **전략 1 (직접 교체)**

이 프로젝트는 초기 단계로 데이터량이 크지 않아 1회성 변환 비용이 낮고, 제품이 명확히 2분류로
가는 만큼 저장·표현·AI를 모두 2값으로 통일하는 편이 총부채가 가장 작다. "flyway 없음" 제약은
자동화 부재일 뿐, **수기 SQL 1건**으로 충분히 통제 가능하므로 교체를 막는 사유가 되지 못한다.

> 리스크 최소화를 최우선으로 둔다면 전략 2도 유효 → **쭌 최종 판단 요청.**

---

## 5. 마이그레이션 절차 (전략 1 채택 시)

flyway가 없으므로 **DB 콘솔에서 직접 실행하는 네이티브 SQL 1회성 스크립트**로 처리한다.
`doc/migration/` 등에 스크립트로 보관 후, 배포와 순서를 맞춰 실행.

```sql
-- 안 A 기준
UPDATE recipes SET age_group = 'BABY_FOOD'
 WHERE age_group IN ('MONTH_4_6','MONTH_7_9','MONTH_10_12','MONTH_12_18');
UPDATE recipes SET age_group = 'ADULT_FOOD'
 WHERE age_group = 'MONTH_18_PLUS';
```

**실행 순서(다운타임 최소화):**
1. 위 `UPDATE` SQL 먼저 실행 (구 코드는 여전히 5값을 알고 있어 정상 동작)
2. 신 코드(2값 enum) 배포

> 순서를 지키면 "코드가 아는 값 ⊇ DB 값"이 항상 성립해 조회 오류 창(window)이 없다.
> 대안으로 앱 기동 시 `JdbcTemplate`로 위 UPDATE를 실행하는 **멱등 ApplicationRunner**를 두면
> 수기 실행 없이 배포만으로 변환 가능(단, 네이티브 SQL이어야 함 — JPA 로딩은 옛 값에서 터짐).

---

## 6. 변경 영향 범위 (전략 1 + 안 A 채택 시, 승인 후 수정 대상)

- **백엔드**
  - `Recipe.java`: `AgeGroup` enum을 2값으로 교체
  - `RecipeExtractService.java`: AI 프롬프트 2곳의 연령 지시문을 2값(+한국어 매핑)으로 교체
  - (레포지토리/서비스/컨트롤러/DTO는 **시그니처 변경 없음** — 값만 축소되어 그대로 동작)
- **프론트**
  - `Home.jsx`: `AGE_GROUPS` → `[{전체}, {이유식}, {어른 음식}]`
  - `RecipeForm.jsx`: `AGE_GROUPS` 2값 + `blankForm()`·수정 로드 기본값 `'MONTH_4_6'` → `'BABY_FOOD'`
  - `RecipeCard.jsx` / `RecipeDetail.jsx`: **변경 없음** (라벨은 백엔드가 내려줌)
- **데이터**
  - 5절 마이그레이션 SQL 1회 실행

---

## 7. 리스크 & 롤백

- **리스크:** 마이그레이션 미실행 상태로 신 코드 배포 시, 옛 값 행 조회에서 enum 변환 실패 →
  5절 실행 순서(SQL 먼저)로 원천 차단.
- **AI 잔존 위험:** 프롬프트 미수정 시 Claude가 `MONTH_*` 반환 → 저장 단계에서 400.
  프롬프트 수정은 필수 동반 작업(6절 포함).
- **롤백:** 신 코드 → 구 코드로 되돌릴 경우 역변환 SQL 필요. 단, 2값→5값은 정보 손실이라
  원복 불가(모두 `MONTH_4_6` 등 대표값으로만 복원). → **변환 전 `recipes` 백업 권장.**

---

## 8. 승인 요청 (쭌 확정 필요 항목)

1. **매핑**: 안 A(4~18개월=이유식 / 18개월+=어른음식) vs 안 B(12개월+=어른음식) — 권장 **A**
2. **전략**: 전략 1(enum 직접 교체 + 1회 SQL 변환) vs 전략 2(5값 유지 + 2그룹 파생) — 권장 **전략 1**
3. (전략 1 시) 마이그레이션 실행 방식: **수기 SQL** vs **멱등 ApplicationRunner** — 권장 수기 SQL + 사전 백업

> 위 3개 확정 시 즉시 구현 착수 가능. 승인 전 코드 수정은 하지 않습니다.
