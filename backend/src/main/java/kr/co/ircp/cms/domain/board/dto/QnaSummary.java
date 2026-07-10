package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * Q&A 목록 응답 DTO.
 * REQ-BOARD-008: Q&A 목록 조회
 */
public record QnaSummary(
        Long id,
        String title,
        Long questionerId,
        String status,
        boolean isPrivate,
        Instant createdAt,
        // SPEC-CMS-AI-004: AI 스마트 태그 (null 없이 항상 빈 목록 이상).
        List<String> tags
) {
}
