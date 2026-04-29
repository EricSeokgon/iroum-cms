-- REQ-AUTH-014 부서·조직 트리 스키마 마이그레이션
-- SPEC-CMS-002 v0.3.2 §13.A — REQ-AUTH-014-D-1~4
--
-- 주의: V1에서 pgcrypto, pg_trgm 확장이 이미 설치됨.

-- ─────────────────────────────────────────────────────────
-- 1. organization (부서·조직 트리, depth ≤ 5)
-- ─────────────────────────────────────────────────────────
CREATE TABLE organization (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    parent_id   BIGINT       REFERENCES organization(id) ON DELETE RESTRICT,
    depth       INT          NOT NULL DEFAULT 0
                CONSTRAINT chk_org_depth CHECK (depth >= 0 AND depth <= 5),
    path        TEXT         NOT NULL,                -- materialized path: /1/3/7/
    sort_order  INT          NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CONSTRAINT chk_org_status CHECK (status IN ('ACTIVE','INACTIVE','DELETED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_org_parent       ON organization(parent_id)           WHERE deleted_at IS NULL;
CREATE INDEX idx_org_status_sort  ON organization(status, sort_order, name);
CREATE INDEX idx_org_path_trgm    ON organization USING GIN (path gin_trgm_ops);

COMMENT ON TABLE  organization             IS 'REQ-AUTH-014 부서·조직 트리 (depth ≤ 5, materialized path)';
COMMENT ON COLUMN organization.path        IS '/{id}/{id}/... 형식의 구체화 경로 (자손 일괄 조회 최적화)';
COMMENT ON COLUMN organization.code        IS '조직 코드 (UNIQUE, 변경 불가 권장)';
COMMENT ON COLUMN organization.depth       IS '루트=0, 최대 5단계 (DB CHECK 제약)';

-- 루트 조직 시드 (운영 시 실제 명칭으로 변경)
INSERT INTO organization (id, code, name, description, parent_id, depth, path, sort_order, status)
VALUES (1, 'ROOT', '본부', '최상위 조직', NULL, 0, '/1/', 0, 'ACTIVE');

-- id SEQUENCE를 시드 다음 값으로 재설정
SELECT setval('organization_id_seq', 1, true);

-- ─────────────────────────────────────────────────────────
-- 2. organization_history (조직 변경 이력)
-- ─────────────────────────────────────────────────────────
CREATE TABLE organization_history (
    id             BIGSERIAL    PRIMARY KEY,
    org_id         BIGINT       NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    version        INT          NOT NULL,
    snapshot       JSONB        NOT NULL,             -- 변경 시점 전체 스냅샷
    changed_by     BIGINT       REFERENCES users(id),
    changed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    change_summary TEXT,
    UNIQUE (org_id, version)
);

CREATE INDEX idx_org_history_org  ON organization_history(org_id, version DESC);
CREATE INDEX idx_org_history_time ON organization_history(changed_at DESC);

COMMENT ON TABLE  organization_history          IS 'REQ-AUTH-014-D-4 조직 변경 이력 (전체 스냅샷 JSONB)';
COMMENT ON COLUMN organization_history.snapshot IS '변경 시점의 organization 행 전체 JSON 스냅샷';
COMMENT ON COLUMN organization_history.version  IS '조직별 단조 증가 버전 번호 (org_id, version UNIQUE)';

-- ─────────────────────────────────────────────────────────
-- 3. users 테이블에 organization_id 컬럼 추가
-- ─────────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN organization_id BIGINT REFERENCES organization(id);

CREATE INDEX idx_users_organization ON users(organization_id) WHERE deleted_at IS NULL;

COMMENT ON COLUMN users.organization_id IS 'REQ-AUTH-014-D-2 사용자 소속 조직 FK (NULL 허용 — 미배정)';

-- admin 시드 사용자를 ROOT 조직에 자동 배치
UPDATE users
SET organization_id = 1
WHERE username = 'admin';
