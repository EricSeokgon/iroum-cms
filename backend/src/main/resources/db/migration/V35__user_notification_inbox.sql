-- REQ-BOARD-014-D-2: 사용자 인앱(INAPP) 알림 수신함
-- 채널 독립 설계: QNA_ANSWERED 외 타입 확장 가능
CREATE TABLE user_notification_inbox (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(50)  NOT NULL,           -- e.g. QNA_ANSWERED
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    ref_id      BIGINT,                          -- 참조 엔티티 PK (예: qna_id)
    ref_type    VARCHAR(50),                     -- 참조 엔티티 타입 (예: QNA)
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_notification_inbox_user
    ON user_notification_inbox (user_id, is_read, created_at DESC);
