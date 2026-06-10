package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 관리자 댓글 목록 조회용 DTO.
 * SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-001 — 전체 게시판 댓글 모더레이션 목록 항목.
 *
 * <p>MyBatis &lt;constructor&gt; resultMap 으로 직접 매핑된다(record 는 setter 없음).
 * contentPreview 는 SQL LEFT(content, 100) 으로 잘린 미리보기 문자열이다.
 *
 * @param postId          게시글 ID
 * @param postTitle       게시글 제목 (bbs_post JOIN)
 * @param boardCode       게시판 코드 (bbs_master JOIN)
 * @param boardName       게시판 이름 (bbs_master JOIN)
 * @param authorUsername  로그인 작성자 username (익명 댓글은 null)
 * @param contentPreview  댓글 내용 미리보기 (최대 100자)
 */
// @MX:NOTE: [AUTO] 프론트엔드 CommentAdminSummary 인터페이스와 필드명 일치 필요.
public record CommentAdminSummary(
        Long id,
        Long postId,
        String postTitle,
        String boardCode,
        String boardName,
        String authorUsername,
        String contentPreview,
        String status,
        Instant createdAt
) {
}
