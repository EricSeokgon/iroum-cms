package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시판 마스터 수정 요청 DTO.
 * REQ-BOARD-001-U: 게시판 마스터 수정
 */
public record BbsMasterUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        boolean useComment,
        boolean useAttachment,
        @Min(0) @Max(20) int maxAttachmentCount,
        @Min(0) long maxAttachmentSizeKb,
        boolean allowAnonymous,
        boolean allowSecret,
        @Min(1) @Max(200) int pageSize,
        String roleRequiredRead,
        String roleRequiredWrite,
        String status
) {
}
