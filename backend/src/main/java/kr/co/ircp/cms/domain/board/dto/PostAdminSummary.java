package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 관리자 게시글 목록 응답 DTO.
 * SPEC-CMS-POST-MODERATE-001 REQ-PA-001
 */
public record PostAdminSummary(
        Long id,
        Long bbsId,
        String bbsName,
        String title,
        Long authorId,
        String authorName,
        String status,
        Instant createdAt
) {
}
