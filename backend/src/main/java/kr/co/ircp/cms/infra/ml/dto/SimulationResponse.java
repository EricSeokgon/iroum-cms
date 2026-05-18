package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;
import java.util.Map;

/**
 * 사업 시뮬레이션 응답 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — 연도별 성장단계 투영.
 */
public record SimulationResponse(
        List<ProjectionPoint> projection,
        String modelVersion
) {
    /** 연도별 투영 포인트. */
    public record ProjectionPoint(
            int year,
            String stage,
            Map<String, Double> entryProbabilities
    ) {
    }
}
