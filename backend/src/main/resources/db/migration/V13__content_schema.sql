-- SPEC-CMS-004 v0.4: Bundle C 콘텐츠·메뉴·사이트관리 스키마
-- REQ-CONTENT-001~010 핵심 테이블 11개 + menu_permissions FK + 인덱스 + 시드
-- Step 1: site / menu / template / page / content_block / page_history /
--         popup / banner / i18n_resource / seo_redirect

-- ─── 1. site (사이트 마스터) ───────────────────────────────────────────────────
CREATE TABLE site (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    domain              VARCHAR(255) NOT NULL,
    default_language    VARCHAR(10)  NOT NULL DEFAULT 'ko',
    supported_languages JSONB        NOT NULL DEFAULT '["ko","en"]'::jsonb,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_site_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_site_lang   CHECK (default_language IN ('ko','en'))
);

COMMENT ON TABLE  site                    IS '사이트 마스터. 1차 출시는 단일 row(MAIN). REQ-CONTENT-003-D';
COMMENT ON COLUMN site.code               IS '사이트 식별 코드 (예: MAIN)';
COMMENT ON COLUMN site.domain             IS '대표 도메인 (예: www.example.go.kr)';
COMMENT ON COLUMN site.default_language   IS '기본 언어 코드 (ko|en)';
COMMENT ON COLUMN site.supported_languages IS '지원 언어 목록 jsonb 배열 (예: ["ko","en"])';
COMMENT ON COLUMN site.metadata           IS '확장용 jsonb (멀티사이트 옵션 등)';

-- ─── 2. menu (메뉴 트리 — Adjacency List + Materialized Path) ─────────────────
CREATE TABLE menu (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    site_id     BIGINT       NOT NULL REFERENCES site(id) ON DELETE RESTRICT,
    parent_id   BIGINT       REFERENCES menu(id) ON DELETE CASCADE,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    url         VARCHAR(500),
    target      VARCHAR(10)  NOT NULL DEFAULT '_self',
    icon        VARCHAR(100),
    sort_order  INT          NOT NULL DEFAULT 0,
    depth       SMALLINT     NOT NULL DEFAULT 1,
    path        VARCHAR(500) NOT NULL,
    is_visible  BOOLEAN      NOT NULL DEFAULT TRUE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_menu_site_code UNIQUE (site_id, code),
    CONSTRAINT chk_menu_target CHECK (target IN ('_self','_blank')),
    CONSTRAINT chk_menu_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_menu_depth  CHECK (depth BETWEEN 1 AND 5)
);

CREATE INDEX idx_menu_parent_sort ON menu(parent_id, sort_order);
CREATE INDEX idx_menu_site_status ON menu(site_id, status) WHERE is_visible = TRUE;
CREATE INDEX idx_menu_path        ON menu(path text_pattern_ops);

COMMENT ON TABLE  menu           IS '메뉴 트리. Adjacency List + Materialized Path 하이브리드. REQ-CONTENT-001-D';
COMMENT ON COLUMN menu.path      IS '루트→현재까지 id 슬래시 결합 (예: /1/3/12). 트리 일괄 조회·정렬 최적화';
COMMENT ON COLUMN menu.depth     IS '루트=1, 최대 5; 깊이 5 초과는 애플리케이션 레이어에서 거부';
COMMENT ON COLUMN menu.is_visible IS 'FALSE면 시민에게 숨김. 운영자는 항상 접근 가능';
COMMENT ON COLUMN menu.metadata  IS '확장 jsonb (예: public:true — 익명 노출 허용)';

-- ─── 3. menu_permissions FK 추가 (SPEC-CMS-002 §4.2.6 테이블 강화) ──────────
-- SPEC-CMS-002에서 생성된 menu_permissions 테이블에 menu FK를 정식 추가
ALTER TABLE menu_permissions
    ADD CONSTRAINT fk_menu_perms_menu
    FOREIGN KEY (menu_id) REFERENCES menu(id) ON DELETE CASCADE;

-- ─── 4. template (페이지 템플릿) ───────────────────────────────────────────────
CREATE TABLE template (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    layout_type   VARCHAR(50)  NOT NULL,
    html_template TEXT         NOT NULL,
    css_assets    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    js_assets     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    description   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_template_layout CHECK (layout_type IN ('FULL','SIDEBAR_LEFT','SIDEBAR_RIGHT','LANDING','BLANK')),
    CONSTRAINT chk_template_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

COMMENT ON TABLE  template              IS '페이지 템플릿. Mustache 슬롯 기반 레이아웃. REQ-CONTENT-004-D';
COMMENT ON COLUMN template.html_template IS 'Mustache 슬롯: {{HEADER}} {{CONTENT}} {{FOOTER}} 필수';
COMMENT ON COLUMN template.css_assets   IS 'jsonb 배열, 절대/상대 URL — ["/assets/main.css","/assets/sub.css"]';
COMMENT ON COLUMN template.js_assets    IS 'jsonb 배열, 절대/상대 URL';
COMMENT ON COLUMN template.layout_type  IS 'FULL|SIDEBAR_LEFT|SIDEBAR_RIGHT|LANDING|BLANK';

-- ─── 5. page (페이지) ─────────────────────────────────────────────────────────
CREATE TABLE page (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    site_id         BIGINT       NOT NULL REFERENCES site(id) ON DELETE RESTRICT,
    template_id     BIGINT       NOT NULL REFERENCES template(id) ON DELETE RESTRICT,
    menu_id         BIGINT       REFERENCES menu(id) ON DELETE SET NULL,
    code            VARCHAR(100) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at    TIMESTAMPTZ,
    scheduled_at    TIMESTAMPTZ,
    seo_title       VARCHAR(300),
    seo_description VARCHAR(500),
    seo_keywords    VARCHAR(500),
    og_image_url    VARCHAR(500),
    canonical_url   VARCHAR(500),
    current_version INT          NOT NULL DEFAULT 1,
    created_by      BIGINT       NOT NULL,
    updated_by      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_page_site_slug  UNIQUE (site_id, slug),
    CONSTRAINT uq_page_site_code  UNIQUE (site_id, code),
    CONSTRAINT chk_page_status    CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','RETRACTED')),
    CONSTRAINT chk_page_scheduled CHECK (status <> 'SCHEDULED' OR scheduled_at IS NOT NULL),
    CONSTRAINT chk_page_slug      CHECK (slug ~ '^[a-z0-9][a-z0-9\-/]*$')
);

CREATE INDEX idx_page_slug_status ON page(slug, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_page_site_status ON page(site_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_page_scheduled   ON page(scheduled_at) WHERE status = 'SCHEDULED';

COMMENT ON TABLE  page               IS '페이지 마스터. DRAFT→SCHEDULED→PUBLISHED→RETRACTED 생명주기. REQ-CONTENT-005-D';
COMMENT ON COLUMN page.slug          IS 'URL path 일부, 소문자/숫자/하이픈/슬래시만 허용';
COMMENT ON COLUMN page.current_version IS '이력 버전 카운터. 수정 시마다 +1';
COMMENT ON COLUMN page.scheduled_at  IS '예약 발행 시각 (status=SCHEDULED일 때 NOT NULL)';
COMMENT ON COLUMN page.seo_title     IS 'SEO 제목 (권고 ≤ 60자)';
COMMENT ON COLUMN page.seo_description IS 'SEO 설명 (권고 ≤ 160자)';

-- ─── 6. content_block (콘텐츠 블록) ───────────────────────────────────────────
CREATE TABLE content_block (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_id     BIGINT       NOT NULL REFERENCES page(id) ON DELETE CASCADE,
    block_type  VARCHAR(20)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    payload     JSONB        NOT NULL,
    version     INT          NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_block_type CHECK (block_type IN ('RICH_TEXT','IMAGE','HTML','MARKDOWN','EMBED'))
);

CREATE INDEX idx_content_block_page_sort ON content_block(page_id, sort_order);

COMMENT ON TABLE  content_block         IS '페이지 콘텐츠 블록. 5종 블록 타입. REQ-CONTENT-006-D';
COMMENT ON COLUMN content_block.payload IS
  'block_type별 스키마:
   RICH_TEXT: { "html": "<p>..</p>" } (sanitized),
   IMAGE: { "url": "...", "alt": "...", "caption": "..." },
   HTML: { "html": "..." } (관리자 신뢰 — 그대로 출력, sanitize 비적용),
   MARKDOWN: { "md": "..." },
   EMBED: { "provider":"youtube|vimeo|kakaomap", "id":"..." }';
COMMENT ON COLUMN content_block.block_type IS 'RICH_TEXT|IMAGE|HTML|MARKDOWN|EMBED';

-- ─── 7. page_history (페이지 변경 이력 — 풀 스냅샷) ─────────────────────────
CREATE TABLE page_history (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_id        BIGINT       NOT NULL REFERENCES page(id) ON DELETE CASCADE,
    version        INT          NOT NULL,
    snapshot       JSONB        NOT NULL,
    edited_by      BIGINT       NOT NULL,
    edited_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_summary VARCHAR(500),
    CONSTRAINT uq_page_history UNIQUE (page_id, version)
);

CREATE INDEX idx_page_history_page ON page_history(page_id, version DESC);

COMMENT ON TABLE  page_history          IS '페이지 변경 이력. 수정·롤백 시 풀 스냅샷 저장. REQ-CONTENT-005-D-2/7';
COMMENT ON COLUMN page_history.snapshot IS
  'page row + content_block 배열 + i18n_resource 배열을 jsonb로 통째 저장. 롤백 시 그대로 적용.';
COMMENT ON COLUMN page_history.change_summary IS '변경 사유 (예: ROLLBACK_FROM_v3, SLUG_CHANGE)';

-- ─── 8. popup (팝업) ───────────────────────────────────────────────────────────
CREATE TABLE popup (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    site_id            BIGINT       NOT NULL REFERENCES site(id) ON DELETE RESTRICT,
    title              VARCHAR(300) NOT NULL,
    content_html       TEXT         NOT NULL,
    position           VARCHAR(20)  NOT NULL DEFAULT 'CENTER',
    x_offset           INT,
    y_offset           INT,
    width              INT          NOT NULL DEFAULT 400,
    height             INT          NOT NULL DEFAULT 300,
    show_from          TIMESTAMPTZ  NOT NULL,
    show_until         TIMESTAMPTZ  NOT NULL,
    show_today_close   BOOLEAN      NOT NULL DEFAULT TRUE,
    display_priority   INT          NOT NULL DEFAULT 0,
    target_type        VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    target_role_codes  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_popup_position CHECK (position IN ('CENTER','TOP_RIGHT','BOTTOM_RIGHT','TOP_LEFT','BOTTOM_LEFT','CUSTOM')),
    CONSTRAINT chk_popup_target   CHECK (target_type IN ('ALL','MEMBER','ROLE')),
    CONSTRAINT chk_popup_status   CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_popup_period   CHECK (show_from < show_until)
);

CREATE INDEX idx_popup_active_window ON popup(site_id, show_from, show_until)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE  popup                 IS '팝업 마스터. 노출 기간·타겟·우선순위 관리. REQ-CONTENT-008-D';
COMMENT ON COLUMN popup.position        IS 'CENTER|TOP_RIGHT|BOTTOM_RIGHT|TOP_LEFT|BOTTOM_LEFT|CUSTOM';
COMMENT ON COLUMN popup.show_today_close IS 'TRUE면 클라이언트에서 "오늘 그만 보기" 쿠키 설정 가능';
COMMENT ON COLUMN popup.target_type     IS 'ALL=모두, MEMBER=인증사용자, ROLE=특정역할';
COMMENT ON COLUMN popup.target_role_codes IS 'target_type=ROLE일 때 허용 역할 코드 배열';

-- ─── 9. banner (배너) ─────────────────────────────────────────────────────────
CREATE TABLE banner (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    site_id            BIGINT       NOT NULL REFERENCES site(id) ON DELETE RESTRICT,
    banner_group_code  VARCHAR(50)  NOT NULL,
    title              VARCHAR(300) NOT NULL,
    image_url          VARCHAR(500) NOT NULL,
    link_url           VARCHAR(500),
    link_target        VARCHAR(10)  NOT NULL DEFAULT '_self',
    alt_text           VARCHAR(300) NOT NULL,
    display_from       TIMESTAMPTZ  NOT NULL,
    display_until      TIMESTAMPTZ  NOT NULL,
    sort_order         INT          NOT NULL DEFAULT 0,
    click_count        BIGINT       NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_banner_target CHECK (link_target IN ('_self','_blank')),
    CONSTRAINT chk_banner_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_banner_period CHECK (display_from < display_until)
);

CREATE INDEX idx_banner_group_active ON banner(banner_group_code, display_from, display_until)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE  banner                  IS '배너 마스터. 그룹별 슬롯·노출 기간·클릭 로그. REQ-CONTENT-009-D';
COMMENT ON COLUMN banner.banner_group_code IS '예: HOME_HERO, SIDE_TOP, FOOTER_PARTNER 등 사이트 정의 그룹';
COMMENT ON COLUMN banner.alt_text          IS 'KWCAG 2.2 AA 1.1.1 대체텍스트 — NOT NULL 강제';

-- ─── 10. i18n_resource (다국어 리소스) ────────────────────────────────────────
CREATE TABLE i18n_resource (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    namespace   VARCHAR(50)  NOT NULL,
    resource_id BIGINT       NOT NULL,
    language    VARCHAR(10)  NOT NULL,
    field_name  VARCHAR(100) NOT NULL,
    value       TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_i18n         UNIQUE (namespace, resource_id, language, field_name),
    CONSTRAINT chk_i18n_namespace CHECK (namespace IN ('menu','page','popup','banner','content_block','system')),
    CONSTRAINT chk_i18n_language  CHECK (language IN ('ko','en'))
);

CREATE INDEX idx_i18n_lookup ON i18n_resource(namespace, resource_id, language);

COMMENT ON TABLE  i18n_resource           IS '다국어 리소스 정규화 테이블. REQ-CONTENT-010-D';
COMMENT ON COLUMN i18n_resource.namespace IS 'menu|page|popup|banner|content_block|system';
COMMENT ON COLUMN i18n_resource.field_name IS
  '예: menu.name, page.title, popup.content_html, banner.alt_text, content_block.payload.html';

-- ─── 11. seo_redirect (URL 리다이렉트) ────────────────────────────────────────
CREATE TABLE seo_redirect (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_path   VARCHAR(500) NOT NULL UNIQUE,
    to_path     VARCHAR(500) NOT NULL,
    http_status SMALLINT     NOT NULL DEFAULT 301,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_redirect_status CHECK (http_status IN (301, 302))
);

CREATE INDEX idx_seo_redirect_active ON seo_redirect(from_path) WHERE is_active = TRUE;

COMMENT ON TABLE  seo_redirect          IS 'URL 리다이렉트. slug 변경 시 PageService에서 자동 INSERT. REQ-CONTENT-005-D-8';
COMMENT ON COLUMN seo_redirect.reason   IS '변경 사유 (예: SLUG_CHANGE_PAGE_ID:42)';
COMMENT ON COLUMN seo_redirect.from_path IS '구 경로 (slug 포함 전체 path)';

-- ─── 시드: 단일 사이트 ────────────────────────────────────────────────────────
INSERT INTO site (code, name, domain, default_language)
VALUES ('MAIN', '공공기관 메인 사이트', 'www.example.go.kr', 'ko');

-- ─── 시드: Bundle C 권한 카탈로그 (§8 권한 매트릭스 기반) ─────────────────────
INSERT INTO permissions (code, resource, action, description) VALUES
    -- 사이트 관리
    ('SITE:READ',               'SITE',       'READ',    '사이트 정보 조회'),
    ('SITE:WRITE',              'SITE',       'WRITE',   '사이트 정보 수정'),
    -- 메뉴 관리
    ('MENU:READ',               'MENU',       'READ',    '메뉴 트리 조회'),
    ('MENU:WRITE',              'MENU',       'WRITE',   '메뉴 생성·수정·삭제'),
    ('MENU:PERMISSION:WRITE',   'MENU',       'ADMIN',   '메뉴별 권한 매핑 관리'),
    -- 템플릿 관리
    ('TEMPLATE:READ',           'TEMPLATE',   'READ',    '템플릿 조회'),
    ('TEMPLATE:WRITE',          'TEMPLATE',   'WRITE',   '템플릿 생성·수정'),
    -- 페이지 관리
    ('PAGE:READ',               'PAGE',       'READ',    '페이지 조회'),
    ('PAGE:WRITE',              'PAGE',       'WRITE',   '페이지 생성·수정'),
    ('PAGE:PUBLISH',            'PAGE',       'EXECUTE', '페이지 즉시 발행·예약·철회'),
    ('PAGE:HISTORY:READ',       'PAGE',       'READ',    '페이지 변경 이력 조회'),
    ('PAGE:ROLLBACK',           'PAGE',       'EXECUTE', '페이지 버전 롤백'),
    -- 콘텐츠 블록
    ('BLOCK:WRITE',             'PAGE',       'WRITE',   '콘텐츠 블록 생성·수정·정렬'),
    ('BLOCK:WRITE_HTML',        'PAGE',       'ADMIN',   'HTML 블록 작성 (SYSADMIN 전용)'),
    -- 팝업 관리
    ('POPUP:READ',              'POPUP',      'READ',    '팝업 조회'),
    ('POPUP:WRITE',             'POPUP',      'WRITE',   '팝업 생성·수정·삭제'),
    -- 배너 관리
    ('BANNER:READ',             'BANNER',     'READ',    '배너 조회'),
    ('BANNER:WRITE',            'BANNER',     'WRITE',   '배너 생성·수정·삭제'),
    ('BANNER:CLICK',            'BANNER',     'EXECUTE', '배너 클릭 로그 기록'),
    -- 다국어 관리
    ('I18N:WRITE',              'I18N',       'WRITE',   '다국어 리소스 저장'),
    -- SEO
    ('SEO:REDIRECT:WRITE',      'SEO',        'WRITE',   'URL 리다이렉트 관리'),
    -- sitemap
    ('SITEMAP:READ',            'SITEMAP',    'READ',    'sitemap.xml 조회');
