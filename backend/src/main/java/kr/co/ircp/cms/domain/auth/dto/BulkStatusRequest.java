package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 사용자 일괄 상태 변경 요청 DTO.
 *
 * <p>SPEC-CMS-USER-BULK-STATUS-001 — PATCH /api/v1/users/bulk-status.
 * 최대 100건까지 한 번에 상태 변경을 요청한다.
 *
 * @param userIds      대상 사용자 PK 목록 (1~100건)
 * @param targetStatus 변경할 목표 상태 (ACTIVE/INACTIVE/LOCKED/DELETED)
 */
public record BulkStatusRequest(
        @NotEmpty
        @Size(max = 100)
        List<Long> userIds,

        @NotNull
        String targetStatus
) {
}
