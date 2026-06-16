package kr.co.ircp.cms.domain.approval.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 단건 거절 요청 DTO.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-011/012 — 거절 사유는 필수(비어 있으면 400).
 *
 * @param reason 거절 사유 (비어 있을 수 없음)
 */
public record RejectRequest(
        @NotBlank(message = "거절 사유는 필수입니다.")
        String reason
) {
}
