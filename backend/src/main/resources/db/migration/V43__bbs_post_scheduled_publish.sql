-- V43__bbs_post_scheduled_publish.sql
-- SPEC-CMS-POST-SCHEDULE-001: 게시글 예약 발행
-- Page 도메인의 예약 발행 패턴(status=SCHEDULED + scheduled_at)을 bbs_post 에 동일 적용.

-- 1) 예약 시각 컬럼
ALTER TABLE bbs_post ADD COLUMN scheduled_at TIMESTAMPTZ;
COMMENT ON COLUMN bbs_post.scheduled_at IS '예약 발행 시각 (NULL=예약 없음, status=SCHEDULED 일 때만 의미)';

-- 2) status CHECK 제약 재정의 (SCHEDULED 추가)
--    PostgreSQL 은 ALTER CONSTRAINT 로 CHECK 식을 수정할 수 없으므로 DROP 후 ADD.
--    기존 허용 집합의 상위 집합이므로 기존 데이터는 모두 안전.
ALTER TABLE bbs_post DROP CONSTRAINT chk_bbs_post_status;
ALTER TABLE bbs_post ADD CONSTRAINT chk_bbs_post_status
  CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','HIDDEN','DELETED'));

-- 3) 만기 예약 게시글 조회용 부분 인덱스
CREATE INDEX idx_bbs_post_scheduled_due ON bbs_post(scheduled_at)
  WHERE status = 'SCHEDULED' AND deleted_at IS NULL;
