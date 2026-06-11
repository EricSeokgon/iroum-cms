package kr.co.ircp.cms.infra.ml.dto;

/**
 * 사업 시뮬레이션 요청 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — PII 없음.
 * <p>SPEC-CMS-SIM-001 주의: ML 측 PredictionRequest 가 {@code extra="forbid"} 이므로
 * 투영기간(horizonYears)은 ML 요청에 포함하지 않는다. 투영기간은 세션 저장·결과 echo 용도로만 쓰인다.
 */
public record SimulationRequest(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount
) {
}
