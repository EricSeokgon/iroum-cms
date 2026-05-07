-- SPEC-CMS-003 설문조사 스키마
-- REQ-BOARD-013: 설문조사 마스터, 질문, 응답, 답변 테이블

-- 설문조사 마스터
CREATE TABLE survey (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bbs_id            BIGINT       REFERENCES bbs_master(id) ON DELETE SET NULL,
    title             VARCHAR(500) NOT NULL,
    description_html  TEXT,
    description_text  TEXT,
    start_at          TIMESTAMPTZ  NOT NULL,
    end_at            TIMESTAMPTZ  NOT NULL,
    target_role_codes JSONB,
    is_anonymous      BOOLEAN      NOT NULL DEFAULT FALSE,
    max_responses     INT,
    response_count    INT          NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by        BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT chk_survey_status CHECK (status IN ('DRAFT','OPEN','CLOSED','HIDDEN')),
    CONSTRAINT chk_survey_period CHECK (end_at > start_at)
);
CREATE INDEX idx_survey_status_period ON survey(status, start_at, end_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_survey_bbs           ON survey(bbs_id) WHERE bbs_id IS NOT NULL;

-- 설문 질문
CREATE TABLE survey_question (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    survey_id     BIGINT       NOT NULL REFERENCES survey(id) ON DELETE CASCADE,
    question_text TEXT         NOT NULL,
    question_type VARCHAR(20)  NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INT          NOT NULL DEFAULT 0,
    options       JSONB,
    CONSTRAINT chk_question_type CHECK (question_type IN ('SINGLE','MULTI','TEXT','RATING','DATE')),
    CONSTRAINT chk_question_options CHECK (
      (question_type IN ('SINGLE','MULTI') AND options IS NOT NULL) OR
      (question_type IN ('TEXT','RATING','DATE'))
    )
);
CREATE INDEX idx_survey_question_survey ON survey_question(survey_id, sort_order);

-- 설문 응답 헤더
CREATE TABLE survey_response (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    survey_id           BIGINT       NOT NULL REFERENCES survey(id) ON DELETE CASCADE,
    respondent_id       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    respondent_ip_hash  VARCHAR(64)  NOT NULL,
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at        TIMESTAMPTZ,
    CONSTRAINT chk_response_anon CHECK (respondent_id IS NOT NULL OR respondent_ip_hash IS NOT NULL)
);
CREATE INDEX idx_survey_response_survey ON survey_response(survey_id, submitted_at DESC) WHERE submitted_at IS NOT NULL;
CREATE INDEX idx_survey_response_user   ON survey_response(respondent_id, survey_id) WHERE respondent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_survey_response_user_once ON survey_response(survey_id, respondent_id) WHERE respondent_id IS NOT NULL;

-- 설문 응답 상세 (질문별 답변)
CREATE TABLE survey_answer (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    response_id     BIGINT       NOT NULL REFERENCES survey_response(id) ON DELETE CASCADE,
    question_id     BIGINT       NOT NULL REFERENCES survey_question(id) ON DELETE CASCADE,
    answer_text     TEXT,
    answer_options  JSONB,
    answer_rating   SMALLINT,
    answer_date     DATE,
    CONSTRAINT chk_answer_rating CHECK (answer_rating IS NULL OR answer_rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_survey_answer_response ON survey_answer(response_id);
CREATE INDEX idx_survey_answer_question ON survey_answer(question_id);
