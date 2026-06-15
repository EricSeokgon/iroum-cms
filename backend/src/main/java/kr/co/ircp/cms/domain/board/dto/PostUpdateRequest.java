package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

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
        @Size(max = 500) String editReason,
        // SPEC-CMS-AI-004: AI 스마트 태그 (선택, 공백 허용, 최대 5개). null/미전송 시 빈 목록 처리.
        List<String> tags
) {
}
