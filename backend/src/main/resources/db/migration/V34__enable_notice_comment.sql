-- REQ-BOARD-003: 공지사항 게시판 댓글 기능 활성화
-- NOTICE 게시판은 관리자 공지에 대한 사용자 의견 등록을 허용합니다.
-- PUBLICATION(발간자료)은 문서 자료실 특성상 댓글 비활성 상태를 유지합니다.
UPDATE bbs_master
SET use_comment = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE code = 'NOTICE'
  AND use_comment = FALSE;
