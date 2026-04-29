-- REQ-AUTH-018-D-1: 회원정보 접근 로그 테이블 (APPEND-ONLY, 개인정보보호법 무결성)
CREATE TABLE personal_data_access_log (
    id              BIGSERIAL PRIMARY KEY,
    viewer_id       BIGINT       NOT NULL REFERENCES users(id),
    viewer_role     VARCHAR(50),
    target_user_id  BIGINT       NOT NULL REFERENCES users(id),
    accessed_fields JSONB        NOT NULL,           -- ["email", "phone", ...]
    purpose         VARCHAR(50)  NOT NULL CHECK (purpose IN (
                        'BUSINESS_INQUIRY', 'SUPPORT', 'AUDIT', 'SELF_VIEW',
                        'ADMIN_USER_LIST', 'ADMIN_USER_EDIT', 'EXPORT'
                    )),
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    trace_id        VARCHAR(64),
    accessed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- REQ-AUTH-018-D-2: 검색 성능 인덱스
CREATE INDEX idx_pda_target  ON personal_data_access_log(target_user_id, accessed_at DESC);
CREATE INDEX idx_pda_viewer  ON personal_data_access_log(viewer_id,      accessed_at DESC);
CREATE INDEX idx_pda_time    ON personal_data_access_log(accessed_at DESC);
CREATE INDEX idx_pda_purpose ON personal_data_access_log(purpose,        accessed_at DESC);

-- APPEND-ONLY 트리거 (개인정보보호법 §29 무결성 보장)
CREATE OR REPLACE FUNCTION pda_reject_modify() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'personal_data_access_log is APPEND-ONLY (REQ-AUTH-018, 개인정보보호법)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pda_no_update BEFORE UPDATE ON personal_data_access_log
    FOR EACH ROW EXECUTE FUNCTION pda_reject_modify();
CREATE TRIGGER pda_no_delete BEFORE DELETE ON personal_data_access_log
    FOR EACH ROW EXECUTE FUNCTION pda_reject_modify();

-- REQ-AUTH-018-D-3: 6개월 콜드 이관 archive 테이블
CREATE TABLE personal_data_access_log_archive (
    LIKE personal_data_access_log INCLUDING ALL
);

COMMENT ON TABLE personal_data_access_log         IS 'REQ-AUTH-018-D-1 회원정보 접근 로그 (APPEND-ONLY, 개인정보보호법)';
COMMENT ON TABLE personal_data_access_log_archive IS 'REQ-AUTH-018-D-3 6개월 콜드 이관 보관 테이블';
COMMENT ON COLUMN personal_data_access_log.accessed_fields IS 'JSONB 배열 — 실제 조회된 개인정보 필드 목록 (예: ["email","phone"])';
COMMENT ON COLUMN personal_data_access_log.purpose         IS '접근 목적 코드 (개인정보보호법 §18 목적 외 이용 추적)';
