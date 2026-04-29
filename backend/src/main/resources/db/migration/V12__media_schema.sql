-- SPEC-CMS-MEDIA-001 v0.4: 통합 미디어 라이브러리 스키마
-- REQ-MEDIA-001~005 핵심 테이블 5개 + 인덱스
-- Q-2 결정: 1차 LocalFileSystemStorage 단일 구현
-- Q-3 결정: AV_SCAN job_type 미도입 (v0.2+ 후속)

-- ─── 1. media_asset (미디어 자산 마스터) ──────────────────────────────────────
CREATE TABLE media_asset (
    id                    BIGSERIAL    PRIMARY KEY,
    uuid                  UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    type                  VARCHAR(20)  NOT NULL,
    original_filename     VARCHAR(500) NOT NULL,
    stored_path           VARCHAR(500) NOT NULL UNIQUE,
    public_url            VARCHAR(500),
    mime_type             VARCHAR(150) NOT NULL,
    size_bytes            BIGINT       NOT NULL,
    checksum_sha256       VARCHAR(64)  NOT NULL,
    width                 INT,
    height                INT,
    duration_sec          NUMERIC(10,3),
    exif_stripped         BOOLEAN      NOT NULL DEFAULT FALSE,
    webp_path             VARCHAR(500),
    thumbnail_paths       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    alt_text              VARCHAR(500),
    description           TEXT,
    tags                  TEXT[]       NOT NULL DEFAULT '{}',
    copyright_holder      VARCHAR(200),
    license_type          VARCHAR(30)  NOT NULL DEFAULT 'INTERNAL',
    usage_restriction     TEXT,
    uploaded_by           BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    uploaded_from_ip_hash VARCHAR(64),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMPTZ,
    CONSTRAINT chk_media_type    CHECK (type IN ('IMAGE','VIDEO','DOCUMENT','AUDIO')),
    CONSTRAINT chk_media_status  CHECK (status IN ('PROCESSING','READY','ARCHIVED','DELETED')),
    CONSTRAINT chk_media_license CHECK (license_type IN ('CC0','CC_BY','CC_BY_NC','PROPRIETARY','INTERNAL')),
    CONSTRAINT chk_media_size    CHECK (size_bytes > 0 AND size_bytes <= 5368709120),
    CONSTRAINT chk_media_image_alt CHECK (
        type <> 'IMAGE' OR status <> 'READY' OR (alt_text IS NOT NULL AND length(alt_text) > 0)
    )
);

COMMENT ON COLUMN media_asset.uuid                IS '공개 노출용 식별자 (URL, 외부 참조)';
COMMENT ON COLUMN media_asset.stored_path         IS 'webroot 외부 절대 경로 또는 객체 스토리지 키';
COMMENT ON COLUMN media_asset.public_url          IS '공개 자산일 때 CDN 또는 정적 서빙 URL';
COMMENT ON COLUMN media_asset.checksum_sha256     IS '업로드 시점 SHA-256, 무결성 검증·중복 탐지';
COMMENT ON COLUMN media_asset.exif_stripped       IS 'TRUE면 EXIF 메타데이터 제거 완료, 이미지 자산만 의미 있음';
COMMENT ON COLUMN media_asset.webp_path           IS 'IMAGE 타입에서만 채워지는 WebP 변환본 경로';
COMMENT ON COLUMN media_asset.thumbnail_paths     IS '{"small":"path","medium":"path","large":"path"} 형태 jsonb';
COMMENT ON COLUMN media_asset.alt_text            IS '접근성 대체 텍스트 (KWCAG 2.2 1.1.1)';
COMMENT ON COLUMN media_asset.tags                IS 'PostgreSQL TEXT[] 태그 배열, GIN 인덱스로 검색';
COMMENT ON COLUMN media_asset.license_type        IS 'CC0/CC_BY/CC_BY_NC/PROPRIETARY/INTERNAL';
COMMENT ON COLUMN media_asset.uploaded_from_ip_hash IS 'IP 직접 저장 금지 (DAR-005), SHA-256 해시만';
COMMENT ON COLUMN media_asset.status              IS 'PROCESSING(후처리중) → READY → ARCHIVED 또는 DELETED';

-- ─── 2. media_asset_usage (사용처 추적) ──────────────────────────────────────
CREATE TABLE media_asset_usage (
    id              BIGSERIAL   PRIMARY KEY,
    asset_id        BIGINT      NOT NULL REFERENCES media_asset(id) ON DELETE CASCADE,
    used_in         VARCHAR(30) NOT NULL,
    reference_id    BIGINT      NOT NULL,
    reference_table VARCHAR(64) NOT NULL,
    used_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at      TIMESTAMPTZ,
    CONSTRAINT chk_usage_kind CHECK (used_in IN
        ('POST','PAGE','CONTENT_BLOCK','COMMENT','POPUP','BANNER','EMAIL_TEMPLATE','ATTACHMENT')),
    CONSTRAINT uq_asset_usage UNIQUE (asset_id, used_in, reference_id, reference_table)
);

COMMENT ON TABLE  media_asset_usage            IS '미디어 자산 사용처 추적 (Reference Counting). removed_at IS NULL인 행 수가 활성 사용처 수.';
COMMENT ON COLUMN media_asset_usage.used_in    IS '사용 도메인 — POST/PAGE/CONTENT_BLOCK/COMMENT/POPUP/BANNER/EMAIL_TEMPLATE/ATTACHMENT';
COMMENT ON COLUMN media_asset_usage.reference_id    IS '사용처 도메인의 PK (예: bbs_post.id)';
COMMENT ON COLUMN media_asset_usage.reference_table IS '사용처 테이블명 (예: bbs_post)';

-- ─── 3. media_collection (사용자 컬렉션·앨범) ─────────────────────────────────
CREATE TABLE media_collection (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    owner_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_collection_owner_name UNIQUE (owner_id, name)
);

COMMENT ON TABLE media_collection IS '사용자별 미디어 컬렉션. is_public=TRUE는 같은 권한 그룹 내 공유.';

-- ─── 4. media_collection_item (컬렉션 — 자산 매핑) ──────────────────────────
CREATE TABLE media_collection_item (
    collection_id BIGINT      NOT NULL REFERENCES media_collection(id) ON DELETE CASCADE,
    asset_id      BIGINT      NOT NULL REFERENCES media_asset(id) ON DELETE CASCADE,
    sort_order    INT         NOT NULL DEFAULT 0,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, asset_id)
);

-- ─── 5. media_processing_job (비동기 작업 큐) ────────────────────────────────
-- Q-3: v0.2 1차는 AV_SCAN 미도입. job_type = WEBP_CONVERT/THUMBNAIL/EXIF_STRIP 3종.
-- v0.2+ ClamAV 도입 시 CHECK 제약을 ('WEBP_CONVERT','THUMBNAIL','EXIF_STRIP','AV_SCAN')으로 확장
CREATE TABLE media_processing_job (
    id            BIGSERIAL    PRIMARY KEY,
    asset_id      BIGINT       NOT NULL REFERENCES media_asset(id) ON DELETE CASCADE,
    job_type      VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    error_message TEXT,
    CONSTRAINT chk_job_type   CHECK (job_type IN ('WEBP_CONVERT','THUMBNAIL','EXIF_STRIP')),
    CONSTRAINT chk_job_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED'))
);

COMMENT ON TABLE media_processing_job IS '비동기 후처리 작업 큐. PENDING 행을 워커가 폴링. v0.2 1차는 EXIF_STRIP/WEBP_CONVERT/THUMBNAIL 3종, AV_SCAN은 v0.2+ 후속.';

-- ─── 인덱스 ───────────────────────────────────────────────────────────────────
-- media_asset
CREATE INDEX idx_media_type_status_created
    ON media_asset (type, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_media_uploaded_by
    ON media_asset (uploaded_by)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_media_tags_gin
    ON media_asset USING GIN (tags);

CREATE INDEX idx_media_thumb_gin
    ON media_asset USING GIN (thumbnail_paths jsonb_path_ops);

CREATE INDEX idx_media_checksum
    ON media_asset (checksum_sha256);

-- media_asset_usage
CREATE INDEX idx_usage_asset_active
    ON media_asset_usage (asset_id)
    WHERE removed_at IS NULL;

CREATE INDEX idx_usage_reference
    ON media_asset_usage (used_in, reference_id);

-- media_processing_job
CREATE INDEX idx_job_pending
    ON media_processing_job (status, started_at)
    WHERE status = 'PENDING';

-- media_collection
CREATE INDEX idx_collection_owner ON media_collection (owner_id, sort_order);
