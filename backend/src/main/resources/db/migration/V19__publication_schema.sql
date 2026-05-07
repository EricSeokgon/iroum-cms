-- SPEC-CMS-003 발간자료(Publication) 스키마
-- REQ-BOARD-012: 발간자료 카테고리·메타데이터·다운로드 통계·ZIP 아카이브
-- 의존: V10 (bbs_master, bbs_post, bbs_attachment)

-- ─── 1. publication_category (발간자료 카테고리, 계층형 최대 depth 3) ───────
CREATE TABLE publication_category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES publication_category(id) ON DELETE RESTRICT,
    depth SMALLINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pub_cat_depth CHECK (depth BETWEEN 1 AND 3),
    CONSTRAINT chk_pub_cat_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

COMMENT ON TABLE publication_category IS 'SPEC-CMS-003 발간자료 카테고리 (계층형, 최대 depth 3)';

-- depth 자동 계산 트리거 (parent의 depth + 1)
CREATE OR REPLACE FUNCTION publication_category_depth_check()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.parent_id IS NULL THEN
        NEW.depth := 1;
    ELSE
        SELECT depth + 1 INTO NEW.depth
        FROM publication_category
        WHERE id = NEW.parent_id;
        IF NEW.depth > 3 THEN
            RAISE EXCEPTION '발간자료 카테고리 최대 깊이(3)를 초과할 수 없습니다.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pub_cat_depth
    BEFORE INSERT OR UPDATE OF parent_id ON publication_category
    FOR EACH ROW EXECUTE FUNCTION publication_category_depth_check();

-- ─── 2. bbs_post_publication_meta (발간자료 메타, bbs_post 1:1) ──────────────
CREATE TABLE bbs_post_publication_meta (
    post_id                  BIGINT PRIMARY KEY REFERENCES bbs_post(id) ON DELETE CASCADE,
    publication_year         SMALLINT NOT NULL,
    publication_month        SMALLINT,
    document_type            VARCHAR(30) NOT NULL,
    publication_category_id  BIGINT REFERENCES publication_category(id) ON DELETE SET NULL,
    file_count               INT NOT NULL DEFAULT 0,
    isbn                     VARCHAR(30),
    publisher                VARCHAR(200),
    metadata                 JSONB,
    CONSTRAINT chk_pub_year CHECK (publication_year BETWEEN 1900 AND 2100),
    CONSTRAINT chk_pub_month CHECK (publication_month IS NULL OR publication_month BETWEEN 1 AND 12),
    CONSTRAINT chk_doc_type CHECK (document_type IN ('REPORT','BROCHURE','RESEARCH','GUIDE','OTHER'))
);

COMMENT ON TABLE bbs_post_publication_meta IS 'SPEC-CMS-003 발간자료 메타데이터 (bbs_post 1:1 확장)';

-- ─── 3. publication_download_stat (발간자료 다운로드 통계, 일/월별) ──────────
CREATE TABLE publication_download_stat (
    post_id          BIGINT NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    attachment_id    BIGINT NOT NULL REFERENCES bbs_attachment(id) ON DELETE CASCADE,
    stat_date        DATE NOT NULL,
    stat_month       VARCHAR(7) NOT NULL,
    download_count   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, attachment_id, stat_date)
);

COMMENT ON TABLE publication_download_stat IS 'SPEC-CMS-003 발간자료 다운로드 통계 (일별 집계)';

-- ─── 4. publication_zip_archive (ZIP 아카이브, 동기 ≤50MB / 비동기 >50MB, 7일 보관) ──
CREATE TABLE publication_zip_archive (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    download_id      UUID NOT NULL UNIQUE,
    requested_by     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    post_id          BIGINT NOT NULL REFERENCES bbs_post(id) ON DELETE CASCADE,
    asset_uuids      UUID[] NOT NULL,
    zip_file_path    TEXT NOT NULL,
    size_bytes       BIGINT NOT NULL,
    mode             VARCHAR(10) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
    deleted_at       TIMESTAMPTZ,
    download_count   INT NOT NULL DEFAULT 0,
    last_downloaded_at TIMESTAMPTZ,
    CONSTRAINT chk_pza_mode CHECK (mode IN ('SYNC','ASYNC')),
    CONSTRAINT chk_pza_expires CHECK (expires_at > created_at)
);

COMMENT ON TABLE publication_zip_archive IS 'SPEC-CMS-003 ZIP 다운로드 아카이브 (7일 보관 후 만료)';

-- ─── 인덱스 ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_pub_zip_expires ON publication_zip_archive(expires_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_pub_download_stat_post ON publication_download_stat(post_id, stat_date DESC);
CREATE INDEX idx_pub_cat_parent ON publication_category(parent_id);

-- ─── 시드: 발간자료 게시판 마스터 (bbs_master) ───────────────────────────────
INSERT INTO bbs_master (code, name, description, type, use_comment, use_attachment, status)
VALUES ('PUBLICATION', '발간자료', '발간자료/자료실 (REQ-BOARD-012)', 'PUBLICATION', FALSE, TRUE, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;
