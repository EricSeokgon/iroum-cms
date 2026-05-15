-- bbs_master soft-delete 컬럼 추가
-- BbsMasterMapper.xml 에서 deleted_at 참조하나 V10에서 누락됨
ALTER TABLE bbs_master ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_bbs_master_active ON bbs_master(status) WHERE deleted_at IS NULL;
