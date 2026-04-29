package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 게시글 작성 요청 DTO.
 * REQ-BOARD-002-C: 게시글 생성
 */
public record PostCreateRequest(
        @NotNull Long bbsMasterId,
        @NotBlank @Size(max = 500) String title,
        @NotBlank String contentHtml,
        String contentText,
        boolean isNotice,
        Instant noticeFrom,
        Instant noticeUntil,
        boolean isSecret,
        String anonymousName,
        String anonymousPwd,
        String metadata
) {
}
