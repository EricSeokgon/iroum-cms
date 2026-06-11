-- SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-004/NS-005: 발송 상태 추적 컬럼 추가
-- 의존: V35 (user_notification_inbox)
--
-- 배경:
--   V35 에는 발송 성공/실패 구분 컬럼이 없다(기존 행은 모두 성공 INSERT).
--   오류/미발송 목록(REQ-NS-004)과 재발송(REQ-NS-005)을 위해 nullable additive
--   컬럼 delivery_status 1개만 추가한다.
--
-- NULL = SENT(정상 발송) 의미. 기존 행 백필을 수행하지 않는다(SPEC §1.2).
-- 오류 모수 = delivery_status IN ('FAILED','PENDING').
--
-- 주의:
--   Flyway 는 마이그레이션을 트랜잭션 내에서 실행하므로 CREATE INDEX CONCURRENTLY
--   는 사용할 수 없다(트랜잭션 블록 밖에서만 허용). V23 선례와 동일하게 단순
--   CREATE INDEX IF NOT EXISTS 를 사용한다.

ALTER TABLE user_notification_inbox
    ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(10) NULL
        CONSTRAINT chk_uni_delivery_status CHECK (delivery_status IN ('SENT','FAILED','PENDING'));

COMMENT ON COLUMN user_notification_inbox.delivery_status IS
    'SPEC-CMS-NOTIFICATION-STAT-001 발송 상태. NULL=SENT(정상). 오류=FAILED/PENDING.';

-- 오류/미발송 목록(REQ-NS-004) 가속 부분 인덱스 — NULL(정상 발송) 행 제외로 인덱스 슬림화.
CREATE INDEX IF NOT EXISTS idx_uni_delivery_status_non_null
    ON user_notification_inbox (created_at DESC)
    WHERE delivery_status IS NOT NULL;

COMMENT ON INDEX idx_uni_delivery_status_non_null IS
    'SPEC-CMS-NOTIFICATION-STAT-001 오류 목록 가속 — NotificationStatMapper.findErrors';
