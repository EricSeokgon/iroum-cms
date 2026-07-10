-- V54__ai_tag_recommendation.sql
-- SPEC-CMS-AI-004 — AI 스마트 태그 추천 로그 테이블 생성 및 bbs_post·qna 태그 컬럼 추가 (PII 제외, LOG 도메인)

CREATE TABLE ai_tag_recommendation_log (
    id               BIGSERIAL    PRIMARY KEY,
    session_ref      VARCHAR(80)  NOT NULL,            -- 익명 세션 또는 회원ID의 SHA-256 해시 (평문 미저장)
    content_type     VARCHAR(20)  NOT NULL,            -- POST / QNA
    content_hash     VARCHAR(64)  NOT NULL,            -- 추천 입력 본문 SHA-256 해시 (캐시 키 겸용)
    recommended_tags JSONB        NULL,                -- 순서 보존 추천 태그 배열
    ml_scores        JSONB        NULL,                -- {"태그1": 0.92, ...}
    model_version    VARCHAR(20)  NULL,                -- ML 모델 버전
    event_type       VARCHAR(20)  NOT NULL,            -- SUGGESTED / ACCEPTED / REJECTED
    tag_value        VARCHAR(100) NULL,                -- 채택/거부 대상 태그 (피드백 행만, 추천 행은 NULL)
    suggested_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    interacted_at    TIMESTAMPTZ  NULL,                -- 피드백 행만

    CONSTRAINT chk_atrl_event        CHECK (event_type IN ('SUGGESTED', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_atrl_feedback     CHECK (
        (event_type = 'SUGGESTED' AND tag_value IS NULL)
        OR (event_type IN ('ACCEPTED', 'REJECTED') AND tag_value IS NOT NULL)
    ),
    CONSTRAINT chk_atrl_content_type CHECK (content_type IN ('POST', 'QNA'))
);

CREATE INDEX idx_atrl_session    ON ai_tag_recommendation_log (session_ref, suggested_at DESC);
CREATE INDEX idx_atrl_event      ON ai_tag_recommendation_log (event_type, suggested_at DESC);
CREATE INDEX idx_atrl_type_time  ON ai_tag_recommendation_log (content_type, suggested_at);

COMMENT ON TABLE  ai_tag_recommendation_log IS 'AI 스마트 태그 추천/피드백 로그 (SPEC-CMS-AI-004, PII 제외, LOG 도메인)';
COMMENT ON COLUMN ai_tag_recommendation_log.session_ref  IS '익명 세션 또는 회원ID의 SHA-256 해시 — 평문 식별자 미저장';
COMMENT ON COLUMN ai_tag_recommendation_log.content_hash IS '추천 입력 본문 SHA-256 해시 (캐시 키 겸용, 평문 미저장)';

-- bbs_post·qna에 tags 컬럼 추가 (기존 INSERT 무영향: DEFAULT '{}' NOT NULL — media_asset.tags 선례)
ALTER TABLE bbs_post ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE qna      ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}';
