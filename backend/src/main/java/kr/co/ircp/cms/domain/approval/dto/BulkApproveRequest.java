package kr.co.ircp.cms.domain.approval.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 일괄 승인 요청 DTO.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-014.
 *
 * @param userIds 승인 대상 사용자 ID 목록 (비어 있을 수 없음)
 */
public record BulkApproveRequest(
        @NotEmpty(message = "대상 사용자 ID 목록은 필수입니다.")
        List<Long> userIds
) {
}
