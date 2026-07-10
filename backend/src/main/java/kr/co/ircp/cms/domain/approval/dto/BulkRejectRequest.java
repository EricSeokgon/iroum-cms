package kr.co.ircp.cms.domain.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 일괄 거절 요청 DTO.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-015 — 다수 사용자 + 공통 거절 사유.
 *
 * @param userIds 거절 대상 사용자 ID 목록 (비어 있을 수 없음)
 * @param reason  공통 거절 사유 (비어 있을 수 없음)
 */
public record BulkRejectRequest(
        @NotEmpty(message = "대상 사용자 ID 목록은 필수입니다.")
        List<Long> userIds,
        @NotBlank(message = "거절 사유는 필수입니다.")
        String reason
) {
}
