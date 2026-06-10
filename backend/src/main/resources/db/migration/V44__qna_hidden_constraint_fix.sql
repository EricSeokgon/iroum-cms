-- SPEC-CMS-QNA-MODERATE-001: HIDDEN 상태를 chk_qna_answer_set 허용 범위에 추가.
-- HIDDEN은 답변 유무와 무관하게 관리자가 숨김 처리할 수 있어야 하므로 별도 분기로 허용.
ALTER TABLE qna DROP CONSTRAINT chk_qna_answer_set;

ALTER TABLE qna ADD CONSTRAINT chk_qna_answer_set CHECK (
    status = 'HIDDEN'
    OR (status = 'PENDING' AND answer_html IS NULL)
    OR (status IN ('ANSWERED', 'CLOSED') AND answer_html IS NOT NULL AND answered_at IS NOT NULL)
);
