package kr.co.ircp.cms.domain.auth.dto;

import java.util.List;

/**
 * 사용자 일괄 상태 변경 결과 DTO.
 *
 * <p>SPEC-CMS-USER-BULK-STATUS-001 — 부분 실패를 허용하므로 성공·실패 건수와
 * 실패 상세 목록을 함께 반환한다.
 *
 * @param successCount 상태 변경에 성공한 건수
 * @param failureCount 상태 변경에 실패한 건수
 * @param failures     실패 상세 목록 (userId + 사유)
 */
public record BulkStatusResult(
        int successCount,
        int failureCount,
        List<FailureDetail> failures
) {

    /**
     * 개별 사용자 실패 상세.
     *
     * @param userId 실패한 사용자 PK
     * @param reason 실패 사유 (사용자 친화적 메시지)
     */
    public record FailureDetail(Long userId, String reason) {
    }
}
