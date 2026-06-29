package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 게시글 수정 요청 DTO.
 * REQ-BOARD-002-U: 게시글 수정 (변경 이력 저장 포함)
 * SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005: expectedVersion 낙관적 잠금(누락 시 400).
 */
public record PostUpdateRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String contentHtml,
        String contentText,
        boolean isNotice,
        Instant noticeFrom,
        Instant noticeUntil,
        boolean isSecret,
        @Size(max = 500) String editReason,
        /** 클라이언트가 알고 있는 게시물 버전. 서버 현재 버전과 다르면 409. 누락 시 400. */
        @NotNull Integer expectedVersion
) {
}
