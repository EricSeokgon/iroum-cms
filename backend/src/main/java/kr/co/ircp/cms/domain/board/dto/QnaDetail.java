package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * Q&A 단건 상세 응답 DTO.
 * REQ-BOARD-008: Q&A 단건 조회
 */
public record QnaDetail(
        Long id,
        String title,
        String questionHtml,
        String questionText,
        Long questionerId,
        String answerHtml,
        String answerText,
        Long answererId,
        Instant answeredAt,
        boolean isPrivate,
        String status,
        // SPEC-CMS-AI-004: AI 스마트 태그 (null 없이 항상 빈 목록 이상).
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
}
