-- SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-001/002
-- PII 암호화 키 회전 배치 실행 로그 — V25 마이그레이션
--
-- 변경 요약:
--   1. pii_key_rotation_log 테이블 신규 생성 (회전 배치 실행 이력 추적)
--   2. 상태 컬럼: IN_PROGRESS / COMPLETED / FAILED 3가지
--   3. 청크 단위 커밋(chunk-level commit) 전략과 결합하여,
--      부분 실패 시에도 이미 마이그레이션된 row 수가 보존된다.
--
-- 운영 관점:
--   - cron 트리거(@Scheduled, 6개월 주기)마다 1 row INSERT (IN_PROGRESS)
--   - 정상 종료 시 status='COMPLETED', migrated_rows 갱신
--   - 예외 종료 시 status='FAILED', error_message 기록

CREATE TABLE IF NOT EXISTS pii_key_rotation_log (
    id              BIGSERIAL PRIMARY KEY,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMPTZ,
    old_key_version SMALLINT     NOT NULL,
    new_key_version SMALLINT     NOT NULL,
    migrated_rows   INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    error_message   TEXT
);

COMMENT ON TABLE  pii_key_rotation_log                  IS 'PII 암호화 키 회전 배치 실행 로그 (SPEC-CMS-SECURITY-PII-ROTATION-001)';
COMMENT ON COLUMN pii_key_rotation_log.started_at      IS '배치 시작 시각 (UTC)';
COMMENT ON COLUMN pii_key_rotation_log.finished_at     IS '배치 종료 시각 (NULL = 진행 중)';
COMMENT ON COLUMN pii_key_rotation_log.old_key_version IS '회전 전 활성 키 버전 (기록 시점 기준)';
COMMENT ON COLUMN pii_key_rotation_log.new_key_version IS '회전 후 활성 키 버전';
COMMENT ON COLUMN pii_key_rotation_log.migrated_rows   IS '재암호화 완료된 row 수';
COMMENT ON COLUMN pii_key_rotation_log.status          IS '실행 상태: IN_PROGRESS / COMPLETED / FAILED';
COMMENT ON COLUMN pii_key_rotation_log.error_message   IS '실패 시 예외 메시지 (status=FAILED 일 때만 채워짐)';
