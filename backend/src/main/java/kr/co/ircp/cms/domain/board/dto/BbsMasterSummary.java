package kr.co.ircp.cms.domain.board.dto;

import kr.co.ircp.cms.domain.board.entity.BbsType;

import java.time.Instant;

/**
 * 게시판 마스터 목록 조회용 요약 DTO.
 * REQ-BOARD-001-Q-1: 게시판 목록 응답
 */
public record BbsMasterSummary(
        Long id,
        String code,
        String name,
        BbsType type,
        boolean useComment,
        boolean useAttachment,
        String status,
        Instant createdAt
) {
}
