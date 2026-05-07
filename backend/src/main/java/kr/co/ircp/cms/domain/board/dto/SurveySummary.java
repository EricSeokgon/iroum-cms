package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 설문조사 목록 응답 DTO.
 * REQ-BOARD-013-R: 설문 페이징 조회 (제목/상태/기간/응답수)
 */
public record SurveySummary(
        Long id,
        String title,
        String status,
        boolean isAnonymous,
        Integer maxResponses,
        int responseCount,
        Instant startAt,
        Instant endAt,
        Instant createdAt
) {
}
