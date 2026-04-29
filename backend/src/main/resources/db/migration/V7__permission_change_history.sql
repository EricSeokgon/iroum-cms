-- REQ-AUTH-016-D-1 권한 변경 이력 (전용 테이블)
-- SPEC-CMS-002 v0.3.2 §13.A — APPEND-ONLY 권한 변경 이력

CREATE TABLE permission_change_history (
    id BIGSERIAL PRIMARY KEY,
    change_type VARCHAR(40) NOT NULL CHECK (change_type IN (
        'ROLE_ASSIGN', 'ROLE_UNASSIGN',
        'ROLE_PERMISSION_GRANT', 'ROLE_PERMISSION_REVOKE'
    )),
    target_user_id BIGINT REFERENCES users(id),
    target_role_code VARCHAR(50) REFERENCES roles(code),
    target_resource VARCHAR(100) NOT NULL,
    changed_by BIGINT REFERENCES users(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO','WARN','CRITICAL')),
    reason TEXT,
    actor_ip VARCHAR(45),
    trace_id VARCHAR(64)
);

CREATE INDEX idx_pch_target_user ON permission_change_history(target_user_id, changed_at DESC)
    WHERE target_user_id IS NOT NULL;
CREATE INDEX idx_pch_role ON permission_change_history(target_role_code, changed_at DESC)
    WHERE target_role_code IS NOT NULL;
CREATE INDEX idx_pch_changed_at ON permission_change_history(changed_at DESC);
CREATE INDEX idx_pch_critical ON permission_change_history(changed_at DESC)
    WHERE severity = 'CRITICAL';

-- APPEND-ONLY 트리거 (REQ-AUTH-016)
CREATE OR REPLACE FUNCTION pch_reject_modify() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'permission_change_history는 APPEND-ONLY입니다 (REQ-AUTH-016). 수정/삭제 불가.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pch_no_update
    BEFORE UPDATE ON permission_change_history
    FOR EACH ROW EXECUTE FUNCTION pch_reject_modify();

CREATE TRIGGER pch_no_delete
    BEFORE DELETE ON permission_change_history
    FOR EACH ROW EXECUTE FUNCTION pch_reject_modify();

COMMENT ON TABLE permission_change_history IS 'REQ-AUTH-016-D-1 권한 변경 이력 (APPEND-ONLY)';
COMMENT ON COLUMN permission_change_history.change_type IS 'ROLE_ASSIGN/ROLE_UNASSIGN/ROLE_PERMISSION_GRANT/ROLE_PERMISSION_REVOKE';
COMMENT ON COLUMN permission_change_history.target_resource IS '역할 부여/회수 시 역할 코드, 권한 부여/회수 시 권한 코드';
COMMENT ON COLUMN permission_change_history.severity IS 'INFO(일반)/WARN/CRITICAL(SUPER_ADMIN 변경)';
