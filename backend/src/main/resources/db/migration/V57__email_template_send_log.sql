-- V57__email_template_send_log.sql
-- SPEC-CMS-EMAIL-TEMPLATE-001 — 이메일 템플릿 기반 실발송 로그

-- 발송 로그: 템플릿 삭제 시에도 이력 보존(template_id NULL 허용 + 코드 스냅샷).
CREATE TABLE email_template_send_log (
    id              BIGSERIAL    PRIMARY KEY,
    template_id     BIGINT,                -- 템플릿 삭제 시 NULL (이력 보존)
    template_code   VARCHAR(100),          -- 스냅샷 (template_id NULL 이후에도 추적)
    recipient_enc   TEXT         NOT NULL, -- EmailEncryptionService로 암호화된 수신자 이메일
    recipient_hmac  TEXT         NOT NULL, -- 수신자 lookup용 HMAC
    subject         VARCHAR(500),
    status          VARCHAR(20)  NOT NULL, -- SUCCESS|FAILED
    error_message   TEXT,
    retry_count     INT          NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_email_send_log_status CHECK (status IN ('SUCCESS', 'FAILED'))
);
CREATE INDEX idx_email_send_log_template ON email_template_send_log (template_id);
CREATE INDEX idx_email_send_log_status   ON email_template_send_log (status);
CREATE INDEX idx_email_send_log_sent_at  ON email_template_send_log (sent_at DESC);

COMMENT ON TABLE  email_template_send_log IS '이메일 템플릿 실발송 로그 (SPEC-CMS-EMAIL-TEMPLATE-001)';
COMMENT ON COLUMN email_template_send_log.recipient_enc  IS 'AES-256-GCM 암호화된 수신자 이메일 (PII)';
COMMENT ON COLUMN email_template_send_log.recipient_hmac IS '수신자 이메일 HMAC (평문 미저장)';
