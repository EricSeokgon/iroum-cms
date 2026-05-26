package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.QnaAnswerRequest;
import kr.co.ircp.cms.domain.board.dto.QnaCreateRequest;
import kr.co.ircp.cms.domain.board.dto.QnaDetail;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;

/**
 * Q&A 서비스 인터페이스.
 * REQ-BOARD-008: 질문/답변 워크플로
 */
public interface QnaService {

    /** Q&A 목록 페이징 조회 (비공개 항목은 본인/관리자만; mine=true면 본인 작성 Q&A만). */
    PageResponse<QnaSummary> listQnas(
            String status,
            Boolean isPrivate,
            String keyword,
            int page,
            int size,
            Long requesterId,
            boolean isAdmin,
            boolean mine
    );

    /** Q&A 단건 조회 (비공개 + 미권한 시 NotFound로 위장). */
    QnaDetail getQna(Long id, Long requesterId, boolean isAdmin);

    /** Q&A 질문 등록. */
    QnaDetail createQna(QnaCreateRequest request, Long questionerId);

    /** Q&A 답변 등록 (관리자/콘텐츠관리자). */
    QnaDetail answerQna(Long id, QnaAnswerRequest request, Long answererId);

    /** Q&A 종료 (질문자 본인 또는 관리자). */
    void closeQna(Long id, Long requesterId, boolean isAdmin);

    /** Q&A 삭제 — 질문자는 PENDING 상태만, 관리자는 모든 상태 삭제 가능. */
    void deleteQna(Long id, Long requesterId, boolean isAdmin);
}
