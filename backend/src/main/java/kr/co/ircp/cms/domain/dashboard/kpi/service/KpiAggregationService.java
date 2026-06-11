package kr.co.ircp.cms.domain.dashboard.kpi.service;

import java.time.LocalDate;

/**
 * SPEC-CMS-KPI-001 Phase 1: KPI 일/월 집계 서비스.
 *
 * <p>지정 일자 기준으로 전체 KPI 를 집계하여 kpi_value 에 UPSERT 한다.
 * 개별 KPI 실패는 격리되며 batch_execution_log(job_group='STATS')에 실행 이력을 남긴다.
 */
public interface KpiAggregationService {

    /**
     * 지정 일자에 대한 모든 KPI 를 집계한다.
     *
     * <p>처리 KPI:
     * <ul>
     *   <li>FEATURE_USAGE_RATE (일별) — access_log 기능 페이지 비율</li>
     *   <li>FILE_DOWNLOAD_COUNT (일별) — audit_log action='EXPORT' 건수</li>
     *   <li>POLICY_APPLY_CONVERSION_RATE (월별) — SPEC-CMS-007 의존, 준비중(STUB)</li>
     * </ul>
     *
     * <p>각 KPI 는 독립적으로 try-catch 격리된다. 하나가 실패해도 나머지는 계속 집계된다.
     *
     * @param targetDate 집계 대상 일자 (일별 KPI 의 dimension date, 월별 KPI 의 소속 월)
     */
    void aggregateAll(LocalDate targetDate);
}
