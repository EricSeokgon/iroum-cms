package kr.co.ircp.cms.domain.dashboard.kpi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPEC-CMS-KPI-001 Phase 2: 단일 KPI 집계값 응답.
 *
 * <p>AC-022 PII 비노출: 본 record 는 집계값(value/valueText)·차원·집계시각만 노출하며
 * user_id, client_ip 등 개인식별 컬럼을 절대 포함하지 않는다.
 *
 * @param kpiCode      kpi_definition.code
 * @param kpiName      kpi_definition.name
 * @param dimensionJson dimension JSONB 의 text 표현
 * @param value        value_numeric (dataState != READY 이면 null)
 * @param valueText    value_text (수치가 아닌 KPI)
 * @param aggregatedAt calculated_at
 * @param dataState    READY | PREPARING
 */
// @MX:NOTE: [AUTO] KpiValueResponse — PII 미포함 집계값 DTO (AC-022)
public record KpiValueResponse(
        String kpiCode,
        String kpiName,
        String dimensionJson,
        BigDecimal value,
        String valueText,
        LocalDateTime aggregatedAt,
        String dataState
) {

    public static final String READY = "READY";
    public static final String PREPARING = "PREPARING";

    /** 전환율 등 의존 데이터 미존재 시 PREPARING 응답 생성. */
    public static KpiValueResponse preparing(String kpiCode, String kpiName) {
        return new KpiValueResponse(kpiCode, kpiName, null, null, null, null, PREPARING);
    }
}
