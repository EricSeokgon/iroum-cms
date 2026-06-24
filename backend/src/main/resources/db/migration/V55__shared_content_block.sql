-- SPEC-CMS-CONTENT-BLOCK-001: 재사용 가능한 공유 콘텐츠 블록 라이브러리.
--
-- REQ-CB-001 — 고유 slug 를 가진 명명된 재사용 블록을 관리한다.
-- block_type: RICH_TEXT(Jsoup relaxed) / HTML(SUPER_ADMIN 전용) / MARKDOWN(text-only) / EMBED(allowlist 검증).
--
-- 주의: created_by 는 기존 사용자 테이블 users(id) 를 참조한다.
-- (SPEC 초안의 admin_user 테이블은 본 스키마에 존재하지 않음 → users 로 정정.)
CREATE TABLE shared_content_block (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    slug         VARCHAR(100) NOT NULL UNIQUE,
    block_type   VARCHAR(20)  NOT NULL,
    content_html TEXT,
    content_raw  TEXT,
    description  VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_scb_type   CHECK (block_type IN ('RICH_TEXT','HTML','MARKDOWN','EMBED')),
    CONSTRAINT chk_scb_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_scb_slug   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE INDEX idx_scb_status ON shared_content_block(status);
CREATE INDEX idx_scb_type   ON shared_content_block(block_type);

COMMENT ON TABLE shared_content_block IS 'SPEC-CMS-CONTENT-BLOCK-001 재사용 공유 콘텐츠 블록';
