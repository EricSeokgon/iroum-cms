-- REQ-AUTH-017 본인인증(이메일 OTP) + 비밀번호 재설정 스키마
-- SPEC-CMS-002 v0.3.1, Q-1 결정: SMS 채널은 v0.4+, 현재 EMAIL만 지원

CREATE TABLE verification_request (
    id             BIGSERIAL PRIMARY KEY,
    request_id     UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    channel        VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL')),        -- Q-1: SMS는 v0.4+
    target         VARCHAR(255) NOT NULL,
    purpose        VARCHAR(50) NOT NULL CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'IMPORTANT_CHANGE')),
    code_hash      VARCHAR(72) NOT NULL,                                     -- BCrypt(12)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ NOT NULL,
    attempts       INT NOT NULL DEFAULT 0,
    max_attempts   INT NOT NULL DEFAULT 3,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'FAILED')),
    verified_at    TIMESTAMPTZ,
    verified_token VARCHAR(64) UNIQUE,           -- 검증 성공 시 발급 (short-lived 5분)
    requester_ip_hash VARCHAR(64),
    user_agent     TEXT
);

CREATE INDEX idx_vreq_target ON verification_request(target, created_at DESC);
CREATE INDEX idx_vreq_status_expires ON verification_request(status, expires_at);
CREATE INDEX idx_vreq_verified_token ON verification_request(verified_token)
    WHERE verified_token IS NOT NULL;

CREATE TABLE verification_history (
    id              BIGSERIAL PRIMARY KEY,
    target          VARCHAR(255) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    purpose         VARCHAR(50) NOT NULL,
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(100),
    requester_ip_hash VARCHAR(64),
    user_agent      TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vhist_target ON verification_history(target, occurred_at DESC);
CREATE INDEX idx_vhist_ip_recent ON verification_history(requester_ip_hash, occurred_at DESC);

COMMENT ON TABLE verification_request IS 'REQ-AUTH-017-D-1 본인인증 요청 (Q-1 EMAIL only)';
COMMENT ON TABLE verification_history IS 'REQ-AUTH-017-D-5 인증 시도 이력 (IP 부정 시도 차단)';
