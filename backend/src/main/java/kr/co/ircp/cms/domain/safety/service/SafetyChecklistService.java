package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.CheckResultRequest;
import kr.co.ircp.cms.domain.safety.dto.CheckResultResponse;
import kr.co.ircp.cms.domain.safety.dto.ChecklistStatsResponse;

import java.util.List;
import java.util.UUID;

/**
 * 체크리스트 추적 서비스.
 * REQ-SAFETY-004
 */
public interface SafetyChecklistService {

    /** 보고서별 체크리스트 + 진행 상태. */
    List<CheckResultResponse> getChecklistByReport(UUID reportUuid, boolean isAdmin, Long companyId);

    /** 체크 결과 기록·변경. */
    CheckResultResponse upsertCheckResult(UUID reportUuid, Long itemId,
                                          CheckResultRequest request,
                                          Long actorUserId, boolean isAdmin, Long companyId);

    /** 관리자 통계 (REQ-SAFETY-004-D-4). */
    ChecklistStatsResponse getOverallStats();
}
