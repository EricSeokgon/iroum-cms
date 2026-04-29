-- SPEC-CMS-005 v0.2.1 §4.2 audit_log 테이블
-- 1차: 단순 테이블 (운영 시 월별 PARTITION 전환은 별도 마이그레이션)
-- APPEND-ONLY: 트리거가 UPDATE/DELETE를 차단하여 무결성 보장
-- 실행일: 2026-04-29

CREATE TABLE audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    event_time   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actor_id     BIGINT       REFERENCES users(id),
    actor_role   VARCHAR(50),
    action       VARCHAR(50)  NOT NULL CHECK (action IN (
        'CREATE','READ','UPDATE','DELETE',
        'LOGIN','LOGIN_FAILURE','LOGOUT',
        'PERMISSION_CHANGE','PERMISSION_DENIED',
        'PASSWORD_CHANGE','PASSWORD_RESET',
        'TOKEN_REFRESH','TOKEN_REVOKE',
        'EXPORT','BATCH'
    )),
    entity_type  VARCHAR(100),
    entity_id    VARCHAR(100),
    before_value JSONB,
    after_value  JSONB,
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    trace_id     VARCHAR(64),
    severity     VARCHAR(20)  NOT NULL DEFAULT 'INFO'  CHECK (severity IN ('INFO','WARN','CRITICAL')),
    result       VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' CHECK (result IN ('SUCCESS','FAILURE')),
    failure_reason TEXT,
    duration_ms  INT
);

CREATE INDEX idx_audit_log_event_time  ON audit_log(event_time DESC);
CREATE INDEX idx_audit_log_actor       ON audit_log(actor_id, event_time DESC) WHERE actor_id IS NOT NULL;
CREATE INDEX idx_audit_log_critical    ON audit_log(event_time DESC)           WHERE severity = 'CRITICAL';
CREATE INDEX idx_audit_log_action_time ON audit_log(action, event_time DESC);

-- APPEND-ONLY 트리거 함수
CREATE OR REPLACE FUNCTION audit_log_reject_modify() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is APPEND-ONLY (SPEC-CMS-005 v0.2.1 §7.4)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_modify();

CREATE TRIGGER audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_modify();

COMMENT ON TABLE audit_log IS 'SPEC-CMS-005 v0.2.1 감사로그 — APPEND-ONLY, 트리거로 UPDATE/DELETE 차단';
