package kr.co.ircp.cms.domain.dashboard.kpi.service;

import kr.co.ircp.cms.domain.dashboard.kpi.mapper.KpiAggregationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * SPEC-CMS-KPI-001 Phase 1: KPI 집계 서비스 구현.
 *
 * <p>각 KPI 를 독립 try-catch 로 격리하여 하나가 실패해도 나머지를 계속 집계한다(AC-003).
 * 집계 전 기존 값을 kpi_value_history 로 아카이브한다(AC-002).
 * 실행 이력은 batch_execution_log(job_group='STATS')에 기록한다.
 *
 * <p>MV(kpi_aggregation_mv) CONCURRENTLY 리프레시는 트랜잭션 밖에서 실행해야 하므로
 * 본 클래스의 @Transactional aggregateAll 에 포함하지 않고 Job 이 별도로 호출한다.
 */
// @MX:NOTE: [AUTO] KpiAggregationServiceImpl — KPI 단위 실패 격리 + 아카이브 후 UPSERT
// @MX:SPEC: SPEC-CMS-KPI-001 Phase 1 (AC-001/002/003/017) + SPEC-CMS-KPI-002 (운영 활동 4종)
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiAggregationServiceImpl implements KpiAggregationService {

    private final KpiAggregationMapper mapper;

    @Override
    @Transactional
    public void aggregateAll(LocalDate targetDate) {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Long batchId = mapper.insertBatchStart(startedAt);

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        String dimensionJson = dayDimension(targetDate);

        // ── KPI 1: feature_usage_rate (일별) ────────────────────────────────────
        try {
            Long kpiId = requireKpiId("FEATURE_USAGE_RATE");
            mapper.archiveExisting(kpiId, dimensionJson);
            mapper.upsertFeatureUsageRate(kpiId, targetDate, dimensionJson);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("FEATURE_USAGE_RATE: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): FEATURE_USAGE_RATE targetDate={}", targetDate, e);
        }

        // ── KPI 2: file_download_count (일별, audit_log action='EXPORT') ─────────
        try {
            Long kpiId = requireKpiId("FILE_DOWNLOAD_COUNT");
            mapper.archiveExisting(kpiId, dimensionJson);
            mapper.upsertFileDownloadCount(kpiId, targetDate, dimensionJson);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("FILE_DOWNLOAD_COUNT: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): FILE_DOWNLOAD_COUNT targetDate={}", targetDate, e);
        }

        // ── KPI 3: policy_apply_conversion_rate (월별, STUB) ────────────────────
        // SPEC-CMS-007 의존. policy_match_stats_monthly 미존재/빈 테이블이면 값을 적재하지 않고 준비중 처리.
        try {
            boolean ready = mapper.countTable("policy_match_stats_monthly") > 0;
            if (ready) {
                log.info("POLICY_APPLY_CONVERSION_RATE: 의존 테이블 존재 — Phase 3 에서 구현 예정(현재 스킵)");
            } else {
                log.info("POLICY_APPLY_CONVERSION_RATE: PREPARING (SPEC-CMS-007 의존 테이블 없음) — 값 미적재");
            }
            // STUB 단계: 값 미적재. 실패가 아니므로 processed 증가는 하지 않는다(집계 산출물 없음).
        } catch (Exception e) {
            failed++;
            errors.add("POLICY_APPLY_CONVERSION_RATE: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): POLICY_APPLY_CONVERSION_RATE", e);
        }

        // ── SPEC-CMS-KPI-002: 운영 활동 지표 4종(코드 5개) ──────────────────────
        String monthDimensionJson = monthDimension(targetDate);
        String dateText = targetDate.toString();

        // KPI 4: DAU (일별, COUNT DISTINCT user_id)
        try {
            Long kpiId = requireKpiId("DAU");
            mapper.archiveExisting(kpiId, dimensionJson);
            mapper.upsertDau(kpiId, targetDate, dimensionJson);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("DAU: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): DAU targetDate={}", targetDate, e);
        }

        // KPI 5: MAU (월별, COUNT DISTINCT user_id)
        try {
            Long kpiId = requireKpiId("MAU");
            mapper.archiveExisting(kpiId, monthDimensionJson);
            mapper.upsertMau(kpiId, targetDate, monthDimensionJson);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("MAU: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): MAU targetDate={}", targetDate, e);
        }

        // KPI 6: CONTENT_VIEW (일별·유형별, dimension={date,contentType})
        try {
            Long kpiId = requireKpiId("CONTENT_VIEW");
            mapper.archiveExistingByDate(kpiId, dateText);
            mapper.upsertContentView(kpiId, targetDate, dateText);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("CONTENT_VIEW: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): CONTENT_VIEW targetDate={}", targetDate, e);
        }

        // KPI 7: AVG_SESSION_DURATION (일별, 세션별 지속시간 평균)
        try {
            Long kpiId = requireKpiId("AVG_SESSION_DURATION");
            mapper.archiveExisting(kpiId, dimensionJson);
            mapper.upsertAvgSessionDuration(kpiId, targetDate, dimensionJson);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("AVG_SESSION_DURATION: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): AVG_SESSION_DURATION targetDate={}", targetDate, e);
        }

        // KPI 8: API_ERROR_RATE (일별, status_code>=500 비율)
        try {
            Long kpiId = requireKpiId("API_ERROR_RATE");
            mapper.archiveExisting(kpiId, dimensionJson);
            mapper.upsertApiErrorRate(kpiId, targetDate, dimensionJson);
            processed++;
        } catch (Exception e) {
            failed++;
            errors.add("API_ERROR_RATE: " + e.getMessage());
            log.warn("KPI 집계 실패(격리): API_ERROR_RATE targetDate={}", targetDate, e);
        }

        String status = failed == 0 ? "SUCCESS" : "FAILURE";
        String summary = failed == 0
                ? "targetDate=" + targetDate
                : "targetDate=" + targetDate + " | " + String.join(" ; ", errors);
        mapper.updateBatchEnd(batchId, status, processed, failed, summary);
    }

    /** 일별 KPI 의 dimension JSONB: {"date":"YYYY-MM-DD"}. */
    private String dayDimension(LocalDate date) {
        return "{\"date\":\"" + date + "\"}";
    }

    /** 월별 KPI(MAU) 의 dimension JSONB: {"month":"YYYY-MM"}. */
    private String monthDimension(LocalDate date) {
        return String.format("{\"month\":\"%04d-%02d\"}", date.getYear(), date.getMonthValue());
    }

    private Long requireKpiId(String code) {
        Long id = mapper.findKpiIdByCode(code);
        if (id == null) {
            throw new IllegalStateException("kpi_definition 미존재: code=" + code);
        }
        return id;
    }
}
