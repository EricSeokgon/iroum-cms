-- SPEC-CMS-CONTENT-REVISION-001 M1: 게시물 낙관적 잠금 + 리비전 보존 정책 기본값.
--
-- 1) bbs_post.version: 낙관적 잠금 기준 컬럼(REQ-REV-005).
--    기존 행은 최신 이력 버전(bbs_post_history.version)으로 초기화하여
--    이력이 있는 게시물과 버전 의미를 정렬한다. 이력이 없으면 1.
-- 2) system_setting: 엔티티별 리비전 이력 최대 보존 개수 기본값(REQ-REV-006).
--    system_setting 스키마(V14)는 key/value/value_type 컬럼을 사용한다.

-- 1) 낙관적 잠금 버전 컬럼 추가
ALTER TABLE bbs_post ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

-- 기존 행 초기화: 최신 이력 버전으로, 이력이 없으면 1 유지
UPDATE bbs_post p
SET version = COALESCE(
    (SELECT MAX(h.version) FROM bbs_post_history h WHERE h.post_id = p.id),
    1
);

-- 2) 리비전 보존 정책 기본값 (엔티티별 최대 이력 개수)
INSERT INTO system_setting (key, value, value_type, description)
VALUES ('content.revision.maxPerEntity', '50', 'INT',
        '콘텐츠 엔티티별 리비전 이력 최대 보존 개수 (REQ-REV-006)')
ON CONFLICT (key) DO NOTHING;
