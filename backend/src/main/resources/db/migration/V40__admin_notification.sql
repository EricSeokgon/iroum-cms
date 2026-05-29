-- SPEC-CMS-NOTIFICATION-CENTER-001: 관리자 운영 알림 받은편지함
-- 관리자별 운영 알림(승인 요청, 발송 실패, 보안 이벤트 등)을 단일 수신함으로 통합.
-- 시민용 user_notification_inbox(V35)와 의미론적으로 분리.

CREATE TABLE admin_notification (
    id              BIGSERIAL    PRIMARY KEY,
    admin_user_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 분류
    type            VARCHAR(50)  NOT NULL,
        -- 예시: POST_APPROVAL_REQUEST, NOTIFICATION_SEND_FAILED,
        --       INTEGRATION_ERROR, SECURITY_EVENT, POLICY_SYNC_WARNING
    severity        VARCHAR(10)  NOT NULL DEFAULT 'INFO'
                    CHECK (severity IN ('INFO','WARN','ERROR')),

    -- 본문
    title           VARCHAR(200) NOT NULL,
    body            TEXT,

    -- 연관 리소스 (딥링크용)
    ref_type        VARCHAR(50),
        -- 예시: POST, COMMENT, NOTIFICATION_SEND, INTEGRATION_LOG, POLICY_PROGRAM
    ref_id          BIGINT,

    -- 상태 (UNREAD → READ → ARCHIVED, 단방향 전이 권장)
    status          VARCHAR(10)  NOT NULL DEFAULT 'UNREAD'
                    CHECK (status IN ('UNREAD','READ','ARCHIVED')),
    read_at         TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ,

    -- 메타
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_admin_notif_read     CHECK (status <> 'READ'     OR read_at     IS NOT NULL),
    CONSTRAINT chk_admin_notif_archived CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL)
);

-- 목록 조회 최적화: admin_user_id + status + created_at DESC
CREATE INDEX idx_admin_notif_user_status
    ON admin_notification (admin_user_id, status, created_at DESC);

-- 미읽음 수 집계 부분 인덱스 (UNREAD 만 — REQ-NC-005 응답 시간 ≤ 50ms)
CREATE INDEX idx_admin_notif_unread
    ON admin_notification (admin_user_id)
    WHERE status = 'UNREAD';

-- 타입 필터
CREATE INDEX idx_admin_notif_type
    ON admin_notification (admin_user_id, type, created_at DESC);

COMMENT ON TABLE admin_notification IS
  'SPEC-CMS-NOTIFICATION-CENTER-001: 관리자 운영 알림 받은편지함';
COMMENT ON COLUMN admin_notification.ref_type IS
  '딥링크용 리소스 타입. 프론트 라우터가 ref_type → URL 매핑 수행';
COMMENT ON COLUMN admin_notification.status IS
  'UNREAD → READ → ARCHIVED 단방향 전이. HARD DELETE 금지 (감사 추적)';
