package kr.co.ircp.cms.domain.safety.dto;

/**
 * 체크리스트 통계 응답.
 * REQ-SAFETY-004-D-4
 */
public record ChecklistStatsResponse(
        long totalReports,
        long totalItems,
        long doneCount,
        long inProgressCount,
        long blockedCount,
        long naCount,
        double completionRate
) {}
