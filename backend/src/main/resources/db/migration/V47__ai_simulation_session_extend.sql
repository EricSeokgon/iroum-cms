-- SPEC-CMS-SIM-001 — 비회원 창업기업 가상 시뮬레이션 환경 확장
-- AI-001(V29)의 ai_simulation_session 위에 직원수·투영기간·추천정책 컬럼 추가.
-- 평문 IP 미저장 불변식은 유지(client_ip_hash 변경 없음).
ALTER TABLE ai_simulation_session
    ADD COLUMN IF NOT EXISTS employee_count INTEGER,
    ADD COLUMN IF NOT EXISTS horizon_years SMALLINT NOT NULL DEFAULT 3 CHECK (horizon_years IN (3, 5)),
    ADD COLUMN IF NOT EXISTS recommended_policies JSONB;
