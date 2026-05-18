-- SPEC-CMS-AI-001 Step 1 — AI 시뮬레이션 세션
-- 익명 시뮬레이션 세션. 평문 IP 절대 미저장 — SHA-256 해시(64자)만 저장.
-- 24시간 만료 (expires_at 생성 컬럼).
CREATE TABLE ai_simulation_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ksic_code VARCHAR(5) NOT NULL,
    capital_amount BIGINT NOT NULL,
    founding_year INTEGER NOT NULL,
    revenue_amount BIGINT,
    projection_result JSONB,
    pdf_status VARCHAR(20) NOT NULL DEFAULT 'NONE' CHECK (pdf_status IN ('NONE','GENERATING','READY','FAILED')),
    client_ip_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 24시간 만료. PostgreSQL은 timestamptz + interval 이 immutable 이 아니므로
    -- GENERATED STORED 컬럼을 거부한다("generation expression is not immutable").
    -- created_at 과 동일하게 now() 기반 DEFAULT 로 24h 만료를 계산한다
    -- (동일 트랜잭션 시작 시각이므로 expires_at = created_at + 24h 가 보장됨).
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '24 hours')
);
CREATE INDEX idx_ai_simulation_session_ip_hash ON ai_simulation_session(client_ip_hash, created_at DESC);
CREATE INDEX idx_ai_simulation_session_expires ON ai_simulation_session(expires_at);
