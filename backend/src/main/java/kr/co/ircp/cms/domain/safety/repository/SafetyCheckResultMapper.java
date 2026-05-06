package kr.co.ircp.cms.domain.safety.repository;

import kr.co.ircp.cms.domain.safety.dto.CheckResultResponse;
import kr.co.ircp.cms.domain.safety.entity.SafetyCheckResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 체크 결과 MyBatis 매퍼.
 * REQ-SAFETY-004
 */
@Mapper
public interface SafetyCheckResultMapper {

    /** 보고서별 체크리스트 + 진행 상태 (LEFT JOIN safety_check_result). */
    List<CheckResultResponse> findChecklistWithStatusByReportId(@Param("reportId") Long reportId);

    Optional<SafetyCheckResult> findByReportIdAndItemId(
            @Param("reportId") Long reportId,
            @Param("itemId") Long itemId
    );

    /** UPSERT (PostgreSQL ON CONFLICT). */
    void upsert(SafetyCheckResult result);

    // ─── 통계 (REQ-SAFETY-004-D-4) ─────────────────────────────────────────
    long countDoneAcrossAll();
    long countInProgressAcrossAll();
    long countBlockedAcrossAll();
    long countNaAcrossAll();
    long countTotalCheckResults();
    long countTotalReports();
    long countTotalChecklistItems();
}
