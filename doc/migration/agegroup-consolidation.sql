-- ============================================================
-- AgeGroup 통합 마이그레이션: 5단계 → 2단계
-- 설계 근거: doc/agegroup-consolidation-design.md 6절
-- 실행 전 반드시 백업 쿼리를 먼저 실행할 것
-- 자동 실행 금지 — DBA 또는 배포 담당자가 직접 수행
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- STEP 0: 사전 백업
-- 영향 받는 레코드를 별도 테이블에 보관한다.
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recipes_agegroup_backup AS
SELECT id, age_group, title, created_at
FROM recipes
WHERE age_group IN ('MONTH_4_6', 'MONTH_7_9', 'MONTH_10_12', 'MONTH_12_18', 'MONTH_18_PLUS');

-- 백업 건수 확인 (실행 후 숫자를 기록해 둘 것)
-- SELECT COUNT(*), age_group FROM recipes_agegroup_backup GROUP BY age_group;

-- ──────────────────────────────────────────────────────────
-- STEP 1 & 2: 트랜잭션으로 묶어 원자적으로 실행
-- ──────────────────────────────────────────────────────────
START TRANSACTION;

-- BABY_FOOD 매핑: MONTH_4_6 / MONTH_7_9 / MONTH_10_12 / MONTH_12_18 → BABY_FOOD
UPDATE recipes
SET age_group = 'BABY_FOOD'
WHERE age_group IN ('MONTH_4_6', 'MONTH_7_9', 'MONTH_10_12', 'MONTH_12_18');

-- ADULT_FOOD 매핑: MONTH_18_PLUS → ADULT_FOOD
UPDATE recipes
SET age_group = 'ADULT_FOOD'
WHERE age_group = 'MONTH_18_PLUS';

-- ──────────────────────────────────────────────────────────
-- STEP 3: 결과 검증
-- 아래 쿼리 결과에 구 값(MONTH_*)이 남아 있으면 안 된다.
-- ──────────────────────────────────────────────────────────
SELECT age_group, COUNT(*) AS cnt FROM recipes GROUP BY age_group;

COMMIT;

-- ──────────────────────────────────────────────────────────
-- STEP 4: 백업 테이블 삭제 (검증 완료 후 수행)
-- ──────────────────────────────────────────────────────────
-- DROP TABLE IF EXISTS recipes_agegroup_backup;
