package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 게시글 상태 변경 요청 DTO.
 * SPEC-CMS-POST-MODERATE-001 REQ-PA-002
 */
public record PostAdminStatusRequest(
        @NotBlank String status
) {
}
