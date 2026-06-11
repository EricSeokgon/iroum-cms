package kr.co.ircp.cms.domain.dashboard.kpi.job;

import kr.co.ircp.cms.domain.dashboard.kpi.mapper.KpiAggregationMapper;
import kr.co.ircp.cms.domain.dashboard.kpi.service.KpiAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * SPEC-CMS-KPI-001 Phase 1: KPI 일별 집계 배치.
 *
 * <p>매일 04:00 에 전일자 KPI 를 집계하고, 집계 트랜잭션 커밋 후
 * kpi_aggregation_mv 를 CONCURRENTLY 리프레시한다.
 *
 * <p>MV CONCURRENTLY 리프레시는 트랜잭션 블록 밖에서 실행되어야 하므로
 * service.aggregateAll(@Transactional) 완료 이후 별도로 호출한다.
 */
// @MX:NOTE: [AUTO] KpiAggregationJob — 매일 04:00 전일자 KPI 집계 + MV 리프레시 스케줄러
// @MX:SPEC: SPEC-CMS-KPI-001 Phase 1
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiAggregationJob {

    private final KpiAggregationService kpiAggregationService;
    private final KpiAggregationMapper kpiAggregationMapper;

    /** 매일 04:00 — 전일자(어제) KPI 집계. */
    @Scheduled(cron = "0 0 4 * * *")
    public void runDaily() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("KPI 집계 배치 시작: targetDate={}", targetDate);

        kpiAggregationService.aggregateAll(targetDate);

        // 집계 트랜잭션 커밋 후 MV CONCURRENTLY 리프레시 (트랜잭션 밖)
        try {
            kpiAggregationMapper.refreshAggregationMv();
            log.info("kpi_aggregation_mv CONCURRENTLY 리프레시 완료");
        } catch (Exception e) {
            // MV 리프레시 실패는 집계 결과를 무효화하지 않는다 — 다음 사이클에 복구.
            log.warn("kpi_aggregation_mv 리프레시 실패 — 다음 사이클 재시도", e);
        }

        log.info("KPI 집계 배치 종료: targetDate={}", targetDate);
    }
}
