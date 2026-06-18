-- SPEC-CMS-SURVEY-001: 설문 알림 로그 + RBAC 권한/메뉴/설정 시드
-- REQ-SURVEY-011~021
-- 주의: 실제 스키마 컬럼명 검증 완료 (permissions.code, roles.code, role_permissions.role_code,
--       admin_menu.menu_key, system_setting.key/value/value_type).
--       모든 시드 INSERT 에 ON CONFLICT DO NOTHING 적용 → 멱등 재실행 보장 (AC-019).

-- ─────────────────────────────────────────────────────────
-- 1. survey_notification_log (설문 알림 발송 로그 — 멱등성 보장)
--    UNIQUE(survey_id, type) → 동일 설문·동일 유형 중복 발송 차단 (AC-012, AC-018)
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS survey_notification_log (
    id            BIGSERIAL    PRIMARY KEY,
    survey_id     BIGINT       NOT NULL REFERENCES survey(id) ON DELETE CASCADE,
    type          VARCHAR(50)  NOT NULL,
        -- SURVEY_OPENED | SURVEY_CLOSED | SURVEY_RESPONSE_LIMIT
    status        VARCHAR(20)  NOT NULL DEFAULT 'SENT'
                  CONSTRAINT chk_survey_notif_status CHECK (status IN ('SENT','FAILED')),
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_survey_notification_log UNIQUE (survey_id, type)
);

COMMENT ON TABLE  survey_notification_log         IS 'SPEC-CMS-SURVEY-001 REQ-SURVEY-020 설문 알림 발송 로그 (멱등성 보장)';
COMMENT ON COLUMN survey_notification_log.type    IS 'SURVEY_OPENED | SURVEY_CLOSED | SURVEY_RESPONSE_LIMIT';
COMMENT ON COLUMN survey_notification_log.status  IS 'SENT | FAILED — best-effort 발송 결과';

CREATE INDEX IF NOT EXISTS idx_survey_notif_log_survey ON survey_notification_log(survey_id);

-- ─────────────────────────────────────────────────────────
-- 2. SURVEY 권한 시드 (3개) — action 은 CHECK(READ/WRITE/DELETE/EXECUTE/ADMIN) 준수 (AC-015)
--    SURVEY:READ=READ, SURVEY:WRITE=WRITE, SURVEY:EXPORT=EXECUTE
-- ─────────────────────────────────────────────────────────
INSERT INTO permissions (code, resource, action, description) VALUES
    ('SURVEY:READ',   'SURVEY', 'READ',    '설문 결과·응답 조회'),
    ('SURVEY:WRITE',  'SURVEY', 'WRITE',   '설문 생성·수정·삭제'),
    ('SURVEY:EXPORT', 'SURVEY', 'EXECUTE', '설문 결과 CSV 내보내기')
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 3. 역할 × 권한 매핑 시드
--    ADMIN/SUPER_ADMIN → 세 권한 모두, CONTENT_ADMIN → SURVEY:READ/WRITE
--    (SUPER_ADMIN 은 V6에서 전체 권한 자동 부여되나, ADMIN/CONTENT_ADMIN 명시 매핑 필요)
-- ─────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('SURVEY:READ', 'SURVEY:WRITE', 'SURVEY:EXPORT')
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'CONTENT_ADMIN'
  AND p.code IN ('SURVEY:READ', 'SURVEY:WRITE')
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 4. 시스템 설정 시드 (2개) — system_setting(key, value, value_type) (AC-016)
-- ─────────────────────────────────────────────────────────
INSERT INTO system_setting (key, value, value_type, description) VALUES
    ('survey.max_responses_default', '100',  'INT',  '설문 기본 최대 응답 수'),
    ('survey.allow_anonymous',       'true', 'BOOL', '설문 익명 응답 허용 여부')
ON CONFLICT (key) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 5. 어드민 메뉴 시드 — admin_menu(menu_key, name, route_path, sort_order) (AC-017)
--    route_path '/board/surveys', menu_key 'board.surveys' (board 그룹 하위)
-- ─────────────────────────────────────────────────────────
INSERT INTO admin_menu (menu_key, name, parent_key, route_path, sort_order, icon, is_active) VALUES
    ('board.surveys', '설문관리', 'board', '/board/surveys', 63, NULL, TRUE)
ON CONFLICT (menu_key) DO NOTHING;

-- 메뉴↔권한 매핑: 설문관리 메뉴는 SURVEY:READ 보유자에게 노출
INSERT INTO admin_menu_permissions (menu_key, permission_code) VALUES
    ('board.surveys', 'SURVEY:READ')
ON CONFLICT (menu_key, permission_code) DO NOTHING;
