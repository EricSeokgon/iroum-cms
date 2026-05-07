-- Q&A 답변 알림 옵트아웃 (EMAIL만 허용; INAPP는 강제)
-- REQ-BOARD-014-D: Q&A 답변 알림 연동 (멱등성·재시도·옵트아웃)
CREATE TABLE qna_notification_optout (
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel      VARCHAR(20)  NOT NULL,
    opted_out_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, channel),
    CONSTRAINT chk_optout_channel CHECK (channel IN ('EMAIL','KAKAO','SMS'))
);

-- Q&A 알림 발송 로그 (멱등성 + 재시도 추적)
CREATE TABLE qna_notification_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    qna_id       BIGINT       NOT NULL REFERENCES qna(id) ON DELETE CASCADE,
    answerer_id  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    recipient_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel      VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count  SMALLINT     NOT NULL DEFAULT 0,
    last_error   TEXT,
    sent_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_qna_notif_channel CHECK (channel IN ('INAPP','EMAIL','KAKAO','SMS')),
    CONSTRAINT chk_qna_notif_status  CHECK (status IN ('PENDING','SENT','FAILED','DEAD_LETTER'))
);
-- 멱등성: 동일 답변·동일 채널 중복 발송 차단 (SENT 또는 PENDING인 경우)
CREATE UNIQUE INDEX uq_qna_notif_idem ON qna_notification_log(qna_id, answerer_id, channel)
    WHERE status IN ('SENT','PENDING');
CREATE INDEX idx_qna_notif_pending   ON qna_notification_log(status, created_at)
    WHERE status IN ('PENDING','FAILED');
CREATE INDEX idx_qna_notif_recipient ON qna_notification_log(recipient_id, created_at DESC);
