-- SPEC-CMS-003 Bundle B: 게시판·공지·Q&A·FAQ 스키마
-- REQ-BOARD-001~005 핵심 테이블 8개 + 인덱스 + 트리거

-- ─── 1. bbs_master (게시판 마스터) ────────────────────────────────────────────
CREATE TABLE bbs_master (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                     VARCHAR(50)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,
    type                     VARCHAR(20)  NOT NULL,
    use_comment              BOOLEAN      NOT NULL DEFAULT TRUE,
    use_attachment           BOOLEAN      NOT NULL DEFAULT TRUE,
    max_attachment_count     INT          NOT NULL DEFAULT 5,
    max_attachment_size_kb   INT          NOT NULL DEFAULT 10240,
    allow_anonymous          BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_secret             BOOLEAN      NOT NULL DEFAULT FALSE,
    page_size                INT          NOT NULL DEFAULT 20,
    role_required_read       VARCHAR(50),
    role_required_write      VARCHAR(50),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    metadata                 JSONB,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bbs_master_type   CHECK (type   IN ('NORMAL','NOTICE','QNA','FAQ','GALLERY','PUBLICATION','SURVEY')),
    CONSTRAINT chk_bbs_master_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_bbs_master_status ON bbs_master(status);
CREATE INDEX idx_bbs_master_type   ON bbs_master(type) WHERE status = 'ACTIVE';
COMMENT ON COLUMN bbs_master.allow_secret IS 'Q&A 등 비공개 게시글 허용';
COMMENT ON COLUMN bbs_master.metadata     IS '확장용 jsonb (다국어 name, 커스텀 정책 등)';

-- ─── 2. bbs_post (게시글) ─────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE bbs_post (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bbs_id          BIGINT       NOT NULL REFERENCES bbs_master(id) ON DELETE RESTRICT,
    title           VARCHAR(500) NOT NULL,
    content_html    TEXT         NOT NULL,
    content_text    TEXT         NOT NULL,
    search_vector   TSVECTOR,
    category_code   VARCHAR(50),
    author_id       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    author_name     VARCHAR(100),
    is_notice       BOOLEAN      NOT NULL DEFAULT FALSE,
    notice_from     TIMESTAMPTZ,
    notice_until    TIMESTAMPTZ,
    is_secret       BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    like_count      BIGINT       NOT NULL DEFAULT 0,
    comment_count   INT          NOT NULL DEFAULT 0,
    attachment_count INT         NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_bbs_post_status CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN','DELETED')),
    CONSTRAINT chk_bbs_post_notice_period CHECK (
      notice_until IS NULL OR notice_from IS NULL OR notice_until > notice_from
    )
);

-- 활성 게시글 인덱스
CREATE INDEX idx_bbs_post_active ON bbs_post(bbs_id, created_at DESC)
  WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_bbs_post_notice_active ON bbs_post(bbs_id, notice_from, notice_until)
  WHERE is_notice = TRUE AND status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_bbs_post_author    ON bbs_post(author_id, created_at DESC);
CREATE INDEX idx_bbs_post_category  ON bbs_post(bbs_id, category_code) WHERE deleted_at IS NULL;

-- 풀텍스트 검색 GIN 인덱스
CREATE INDEX idx_bbs_post_search_vector ON bbs_post USING GIN (search_vector);
CREATE INDEX idx_bbs_post_title_trgm ON bbs_post USING GIN (title gin_trgm_ops);

-- search_vector 자동 업데이트 트리거 (PostgreSQL 16 특화: tsvector 갱신)
CREATE OR REPLACE FUNCTION bbs_post_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('simple', COALESCE(NEW.title, '')), 'A') ||
    setweight(to_tsvector('simple', COALESCE(NEW.content_text, '')), 'B');
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bbs_post_search_vector
BEFORE INSERT OR UPDATE OF title, content_text ON bbs_post
FOR EACH ROW EXECUTE FUNCTION bbs_post_search_vector_update();

-- ─── 3. bbs_comment (댓글 + 1단계 대댓글) ────────────────────────────────────
CREATE TABLE bbs_comment (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id           BIGINT       NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    parent_comment_id BIGINT       REFERENCES bbs_comment(id) ON DELETE CASCADE,
    author_id         BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    anonymous_name    VARCHAR(100),
    anonymous_pwd_hash VARCHAR(60),
    content           TEXT         NOT NULL,
    ip_address        INET,
    status            VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT chk_comment_status   CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
    CONSTRAINT chk_comment_identity CHECK (
      author_id IS NOT NULL OR (anonymous_name IS NOT NULL AND anonymous_pwd_hash IS NOT NULL)
    )
);
CREATE INDEX idx_bbs_comment_post   ON bbs_comment(post_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_bbs_comment_parent ON bbs_comment(parent_comment_id) WHERE parent_comment_id IS NOT NULL;
CREATE INDEX idx_bbs_comment_author ON bbs_comment(author_id, created_at DESC);

-- 1단계 대댓글 강제(자식의 자식 금지) 트리거
CREATE OR REPLACE FUNCTION bbs_comment_depth_check() RETURNS trigger AS $$
DECLARE parent_parent BIGINT;
BEGIN
  IF NEW.parent_comment_id IS NOT NULL THEN
    SELECT parent_comment_id INTO parent_parent FROM bbs_comment WHERE id = NEW.parent_comment_id;
    IF parent_parent IS NOT NULL THEN
      RAISE EXCEPTION 'COMMENT_DEPTH_EXCEEDED: 1단계 대댓글까지만 허용';
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_bbs_comment_depth BEFORE INSERT ON bbs_comment
FOR EACH ROW EXECUTE FUNCTION bbs_comment_depth_check();

-- ─── 4. bbs_attachment (첨부파일) ────────────────────────────────────────────
CREATE TABLE bbs_attachment (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id           BIGINT       REFERENCES bbs_post(id) ON DELETE CASCADE,
    comment_id        BIGINT       REFERENCES bbs_comment(id) ON DELETE CASCADE,
    file_name         VARCHAR(500) NOT NULL,
    stored_path       VARCHAR(500) NOT NULL UNIQUE,
    mime_type         VARCHAR(150) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   VARCHAR(64)  NOT NULL,
    scan_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    scan_completed_at TIMESTAMPTZ,
    download_count    BIGINT       NOT NULL DEFAULT 0,
    uploaded_by       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT chk_att_size CHECK (size_bytes > 0 AND size_bytes <= 104857600),
    CONSTRAINT chk_att_scan CHECK (scan_status IN ('PENDING','CLEAN','INFECTED','SCAN_FAILED','SKIPPED')),
    CONSTRAINT chk_att_owner CHECK (
      (post_id IS NOT NULL AND comment_id IS NULL) OR
      (post_id IS NULL AND comment_id IS NOT NULL) OR
      (post_id IS NULL AND comment_id IS NULL)
    )
);
CREATE INDEX idx_attachment_post     ON bbs_attachment(post_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_attachment_comment  ON bbs_attachment(comment_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_attachment_uploaded ON bbs_attachment(uploaded_at DESC);
CREATE INDEX idx_attachment_pending  ON bbs_attachment(scan_status, uploaded_at)
  WHERE scan_status = 'PENDING';
COMMENT ON COLUMN bbs_attachment.stored_path     IS 'webroot 외부 절대 경로 또는 객체 스토리지 키';
COMMENT ON COLUMN bbs_attachment.checksum_sha256 IS '업로드 시점 SHA-256, 다운로드 검증에 사용';

-- ─── 5. faq (FAQ) ─────────────────────────────────────────────────────────────
CREATE TABLE faq (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_code VARCHAR(50)  NOT NULL,
    question      VARCHAR(500) NOT NULL,
    answer_html   TEXT         NOT NULL,
    answer_text   TEXT         NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    view_count    BIGINT       NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    metadata      JSONB,
    created_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT chk_faq_status CHECK (status IN ('PUBLISHED','HIDDEN','DELETED'))
);
CREATE INDEX idx_faq_category ON faq(category_code, sort_order)
  WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_faq_question_trgm ON faq USING GIN (question gin_trgm_ops);

-- ─── 6. qna (Q&A) ─────────────────────────────────────────────────────────────
CREATE TABLE qna (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title         VARCHAR(500) NOT NULL,
    question_html TEXT         NOT NULL,
    question_text TEXT         NOT NULL,
    questioner_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    answerer_id   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    answer_html   TEXT,
    answer_text   TEXT,
    answered_at   TIMESTAMPTZ,
    is_private    BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT chk_qna_status     CHECK (status IN ('PENDING','ANSWERED','CLOSED','HIDDEN')),
    CONSTRAINT chk_qna_answer_set CHECK (
      (status = 'PENDING' AND answer_html IS NULL) OR
      (status IN ('ANSWERED','CLOSED') AND answer_html IS NOT NULL AND answered_at IS NOT NULL)
    )
);
CREATE INDEX idx_qna_status_created ON qna(status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_qna_questioner     ON qna(questioner_id, created_at DESC);
CREATE INDEX idx_qna_answerer       ON qna(answerer_id) WHERE answerer_id IS NOT NULL;
CREATE INDEX idx_qna_title_trgm     ON qna USING GIN (title gin_trgm_ops);

-- ─── 7. bbs_post_history (게시글 변경 이력) ──────────────────────────────────
CREATE TABLE bbs_post_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id      BIGINT       NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    version      INT          NOT NULL,
    title        VARCHAR(500) NOT NULL,
    content_html TEXT         NOT NULL,
    edited_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    edit_reason  VARCHAR(200),
    edited_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, version)
);
CREATE INDEX idx_post_history_post ON bbs_post_history(post_id, version DESC);

-- ─── 8. bbs_view_log (조회 이력) ─────────────────────────────────────────────
CREATE TABLE bbs_view_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id         BIGINT       NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    user_id         BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    ip_hash         VARCHAR(64)  NOT NULL,
    user_agent_hash VARCHAR(64)  NOT NULL,
    viewed_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_view_log_post_time ON bbs_view_log(post_id, viewed_at DESC);
CREATE INDEX idx_view_log_user_time ON bbs_view_log(user_id, viewed_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_view_log_dedupe ON bbs_view_log(post_id, COALESCE(user_id, 0), ip_hash, viewed_at DESC);
