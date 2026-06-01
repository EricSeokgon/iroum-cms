-- SPEC-CMS-NOTICE-I18N-001: 공지사항 게시글 다국어 번역 테이블
CREATE TABLE bbs_post_i18n (
    id           BIGSERIAL    PRIMARY KEY,
    post_id      BIGINT       NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    language     VARCHAR(5)   NOT NULL,
    title        VARCHAR(500) NOT NULL,
    content_html TEXT,
    content_text TEXT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_post_i18n UNIQUE (post_id, language),
    CONSTRAINT chk_post_i18n_lang CHECK (language IN ('ko', 'en'))
);

CREATE INDEX idx_bbs_post_i18n_post ON bbs_post_i18n(post_id);

COMMENT ON TABLE bbs_post_i18n IS 'SPEC-CMS-NOTICE-I18N-001 — 게시글 다국어 번역 (ko 원본은 bbs_post에 저장)';
