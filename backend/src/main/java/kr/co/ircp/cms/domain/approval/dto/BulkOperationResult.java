package kr.co.ircp.cms.domain.approval.dto;

import java.util.List;

/**
 * 일괄 승인/거절 처리 결과 DTO.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-016 — 건별 성공/실패를 집계하여 반환한다
 * (개별 실패가 전체 트랜잭션을 롤백하지 않음).
 *
 * @param successCount 성공 건수
 * @param failureCount 실패 건수
 * @param failures     실패 상세 목록
 */
public record BulkOperationResult(
        int successCount,
        int failureCount,
        List<Failure> failures
) {
    /**
     * 건별 실패 상세.
     *
     * @param userId 실패한 사용자 ID
     * @param error  실패 사유 메시지
     */
    public record Failure(Long userId, String error) {
    }
}
