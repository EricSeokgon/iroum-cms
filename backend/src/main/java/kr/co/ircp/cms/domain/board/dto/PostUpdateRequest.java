package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 게시글 수정 요청 DTO.
 * REQ-BOARD-002-U: 게시글 수정 (변경 이력 저장 포함)
 */
public record PostUpdateRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String contentHtml,
        String contentText,
        boolean isNotice,
        Instant noticeFrom,
        Instant noticeUntil,
        boolean isSecret,
        @Size(max = 500) String editReason
) {
}
