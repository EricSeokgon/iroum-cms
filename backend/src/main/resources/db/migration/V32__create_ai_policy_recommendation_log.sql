-- V32__create_ai_policy_recommendation_log.sql
-- SPEC-CMS-AI-002 — AI 정책 추천/피드백 로그 (PII 제외, LOG 도메인)
CREATE TABLE ai_policy_recommendation_log (
    id                     BIGSERIAL    PRIMARY KEY,
    session_ref            VARCHAR(80)  NOT NULL,            -- 익명 세션 해시 또는 회원ID 해시 (SHA-256, 평문 미저장)
    company_profile        JSONB        NOT NULL,            -- 입력 프로필 (업종코드/규모/성장단계/지역, PII 제외)
    query_text             VARCHAR(500) NULL,                -- 선택적 자연어 검색어
    recommended_policy_ids JSONB        NULL,                -- 순서 보존 추천 정책 ID 배열 [101, 88, 203, ...]
    ml_scores              JSONB        NULL,                -- {"101": {"semantic":0.82,"rule":0.74,"hybrid":0.79}, ...}
    interaction_type       VARCHAR(20)  NOT NULL,            -- VIEWED / CLICKED / APPLIED / DISMISSED
    policy_id              BIGINT       NULL,                -- 상호작용 대상 정책 ID (피드백 행만, 추천 행은 NULL)
    recommended_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    interacted_at          TIMESTAMPTZ  NULL,                -- 상호작용 발생 시각 (피드백 행만)
    CONSTRAINT chk_aprl_interaction CHECK (interaction_type IN ('VIEWED','CLICKED','APPLIED','DISMISSED')),
    CONSTRAINT chk_aprl_feedback    CHECK (
        (interaction_type = 'VIEWED'  AND policy_id IS NULL)
     OR (interaction_type <> 'VIEWED' AND policy_id IS NOT NULL)
    ),
    CONSTRAINT chk_aprl_session_len CHECK (char_length(session_ref) BETWEEN 1 AND 80)
);

CREATE INDEX idx_aprl_session       ON ai_policy_recommendation_log(session_ref, recommended_at DESC);
CREATE INDEX idx_aprl_type_time     ON ai_policy_recommendation_log(interaction_type, recommended_at DESC);
CREATE INDEX idx_aprl_policy_time   ON ai_policy_recommendation_log(policy_id, interacted_at DESC)
    WHERE policy_id IS NOT NULL;
CREATE INDEX idx_aprl_metrics_day   ON ai_policy_recommendation_log(recommended_at);

COMMENT ON TABLE  ai_policy_recommendation_log IS 'AI 정책 추천/피드백 로그 (SPEC-CMS-AI-002, PII 제외, LOG 도메인)';
COMMENT ON COLUMN ai_policy_recommendation_log.session_ref     IS '익명 세션 또는 회원ID의 SHA-256 해시 — 평문 식별자 미저장';
COMMENT ON COLUMN ai_policy_recommendation_log.company_profile IS '추천 입력 스냅샷 (ksic_code/employee_count/growth_stage/region_code/annual_revenue, 대표자명·식별번호 금지)';
