package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Q&A 상태 변경 요청 DTO.
 * SPEC-CMS-QNA-MODERATE-001 REQ-QNA-ADM-002
 */
public record QnaAdminStatusRequest(
        @NotBlank String status
) {
}
