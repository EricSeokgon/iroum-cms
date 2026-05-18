-- V33__ai_rag_query_log_and_policy_embedding.sql
-- SPEC-CMS-AI-003 — RAG 질의응답 (pgvector 확장 + 임베딩 컬럼 + 질의 로그, PII 제외, LOG 도메인)
-- 단일 마이그레이션 (Exclusion #6): pgvector 확장 + policy_program 임베딩 컬럼 + ai_rag_query_log.

-- 1) pgvector 확장 활성화 (PostgreSQL 16). 미설치 환경에서도 IT는 SKIP되므로 IF NOT EXISTS.
CREATE EXTENSION IF NOT EXISTS vector;

-- 2) policy_program 임베딩 컬럼 추가 (별도 테이블 미생성 — 1:1 컬럼 추가 방식)
ALTER TABLE policy_program
    ADD COLUMN embed_vector        vector(384)  NULL,
    ADD COLUMN embedded_at         TIMESTAMPTZ  NULL,
    ADD COLUMN embed_model_version VARCHAR(64)  NULL;

COMMENT ON COLUMN policy_program.embed_vector        IS '정책 문서 임베딩 벡터 (sentence embedding 384차원, NULL=미생성)';
COMMENT ON COLUMN policy_program.embedded_at         IS '임베딩 생성 시점 (NULL=미생성, 재임베딩 시 갱신)';
COMMENT ON COLUMN policy_program.embed_model_version IS '임베딩 생성에 사용된 모델 버전 식별자';

-- pgvector cosine similarity 검색용 IVFFlat 인덱스 (운영 데이터 적재 후 lists 튜닝 전제)
CREATE INDEX idx_policy_program_embed_cosine
    ON policy_program USING ivfflat (embed_vector vector_cosine_ops)
    WITH (lists = 100);

-- 3) RAG 질의/피드백 로그 (PII 제외, LOG 도메인)
--    query_ref: 클라이언트 반환 UUID. 피드백(AC-RAG-004)이 비동기 적재 행을 멱등 갱신하는 상관키.
CREATE TABLE ai_rag_query_log (
    id                   BIGSERIAL    PRIMARY KEY,
    query_ref            VARCHAR(64)  NOT NULL UNIQUE,         -- 클라이언트 반환 UUID (피드백 상관키)
    question_hash        VARCHAR(80)  NOT NULL,                -- 질문 텍스트 SHA-256 해시 (평문 미저장, 캐시 키 겸용)
    session_ref          VARCHAR(80)  NOT NULL,                -- 익명 세션 또는 회원ID SHA-256 해시 (평문 미저장)
    retrieved_policy_ids JSONB        NULL,                    -- 검색된 정책 ID 배열 [101, 88, 203, ...]
    answer_quality_score SMALLINT     NULL,                    -- 응답 품질 점수 0~100 (ML/규칙 산출, NULL 허용)
    feedback             VARCHAR(20)  NULL,                    -- HELPFUL / UNHELPFUL (NULL=미응답)
    latency_ms           INTEGER      NOT NULL,                -- 전체 처리 지연(ms)
    cache_hit            BOOLEAN      NOT NULL DEFAULT FALSE,  -- ragQueryCache 히트 여부 (메트릭 집계용)
    degraded             BOOLEAN      NOT NULL DEFAULT FALSE,  -- FTS 단독 폴백 여부
    queried_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    feedback_at          TIMESTAMPTZ  NULL,                    -- 피드백 발생 시각 (NULL=미응답)
    CONSTRAINT chk_arql_feedback       CHECK (feedback IS NULL OR feedback IN ('HELPFUL','UNHELPFUL')),
    CONSTRAINT chk_arql_quality_range  CHECK (answer_quality_score IS NULL OR answer_quality_score BETWEEN 0 AND 100),
    CONSTRAINT chk_arql_qhash_len      CHECK (char_length(question_hash) BETWEEN 1 AND 80),
    CONSTRAINT chk_arql_session_len    CHECK (char_length(session_ref)   BETWEEN 1 AND 80),
    CONSTRAINT chk_arql_feedback_pair  CHECK (
        (feedback IS NULL AND feedback_at IS NULL)
     OR (feedback IS NOT NULL AND feedback_at IS NOT NULL)
    )
);

CREATE INDEX idx_arql_qhash        ON ai_rag_query_log(question_hash, queried_at DESC);
CREATE INDEX idx_arql_session      ON ai_rag_query_log(session_ref, queried_at DESC);
CREATE INDEX idx_arql_feedback     ON ai_rag_query_log(feedback, feedback_at DESC) WHERE feedback IS NOT NULL;
CREATE INDEX idx_arql_metrics_day  ON ai_rag_query_log(queried_at);
CREATE INDEX idx_arql_degraded     ON ai_rag_query_log(degraded, queried_at DESC);

COMMENT ON TABLE  ai_rag_query_log               IS 'RAG 질의/피드백 로그 (SPEC-CMS-AI-003, PII 제외, LOG 도메인)';
COMMENT ON COLUMN ai_rag_query_log.query_ref     IS '클라이언트 반환 UUID — 피드백(AC-RAG-004) 상관키, 평문 식별자 아님';
COMMENT ON COLUMN ai_rag_query_log.question_hash IS '질문 텍스트 SHA-256 해시 — 평문 미저장, ragQueryCache 키와 동일 산식';
COMMENT ON COLUMN ai_rag_query_log.session_ref   IS '익명 세션 또는 회원ID의 SHA-256 해시 — 평문 식별자 미저장 (IpHashUtil 재사용)';
COMMENT ON COLUMN ai_rag_query_log.degraded      IS 'true=ML 장애로 FTS 단독 폴백 응답';
