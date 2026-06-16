-- V56__email_template.sql
-- SPEC-CMS-EMAIL-TEMPLATE-001 — 이메일 템플릿 + SMTP 동적 설정 테이블 생성

-- 이메일 템플릿: 관리자 CRUD 대상. code+language 유니크.
CREATE TABLE email_template (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    template_type VARCHAR(40)  NOT NULL,   -- OTP|QNA_ANSWER|PASSWORD_RESET|ADMIN_NOTIFICATION|CUSTOM
    language      VARCHAR(10)  NOT NULL DEFAULT 'ko',
    subject       VARCHAR(500) NOT NULL,
    body_html     TEXT         NOT NULL,
    body_text     TEXT,                    -- fallback 평문
    variables     JSONB,                   -- 필수 변수 정의 [{name, required, description}]
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    BIGINT,
    updated_by    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_template_code_lang UNIQUE (code, language),
    CONSTRAINT chk_email_template_type CHECK (
        template_type IN ('OTP', 'QNA_ANSWER', 'PASSWORD_RESET', 'ADMIN_NOTIFICATION', 'CUSTOM')
    )
);
CREATE INDEX idx_email_template_type   ON email_template (template_type);
CREATE INDEX idx_email_template_active ON email_template (is_active);

COMMENT ON TABLE  email_template IS '관리자 이메일 템플릿 (SPEC-CMS-EMAIL-TEMPLATE-001)';
COMMENT ON COLUMN email_template.variables IS '필수 변수 정의 JSONB 배열 [{name, required, description}]';
COMMENT ON COLUMN email_template.body_text IS '템플릿 미존재/HTML 미지원 클라이언트용 fallback 평문';

-- SMTP 동적 설정 — 단일 활성 행 운용. password는 암호화 저장.
CREATE TABLE smtp_config (
    id           BIGSERIAL    PRIMARY KEY,
    host         VARCHAR(200) NOT NULL,
    port         INT          NOT NULL,
    username     VARCHAR(200),
    password_enc TEXT,                     -- EmailEncryptionService로 암호화 저장
    from_address VARCHAR(200) NOT NULL,
    from_name    VARCHAR(100),
    encryption   VARCHAR(20)  NOT NULL DEFAULT 'STARTTLS', -- NONE|SSL|STARTTLS
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_smtp_encryption CHECK (encryption IN ('NONE', 'SSL', 'STARTTLS'))
);

COMMENT ON TABLE smtp_config IS 'SMTP 동적 설정 단일 활성 행 (SPEC-CMS-EMAIL-TEMPLATE-001)';
COMMENT ON COLUMN smtp_config.password_enc IS 'EmailEncryptionService로 암호화된 SMTP 비밀번호';
