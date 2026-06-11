package kr.co.ircp.cms.domain.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 시뮬레이션 시작 요청 DTO.
 *
 * <p>SPEC-CMS-AI-001 — PII 없음. 비식별 필드만 전달한다.
 * <p>SPEC-CMS-SIM-001 — 직원수(employeeCount, 선택)·투영기간(horizonYears: 3 또는 5, 기본 3) 추가.
 *
 * @param employeeCount 직원 수 (선택, nullable)
 * @param horizonYears  투영 기간(년) — 3 또는 5만 허용, 미지정/null 시 3으로 보정
 */
public record SimulationStartDto(
        String ksicCode,
        Long capitalAmount,
        Integer foundingYear,
        Long revenueAmount,
        Integer employeeCount,
        @Min(3) @Max(5) Integer horizonYears
) {
    /** 투영 기간 미지정 시 적용되는 기본값(년). */
    public static final int DEFAULT_HORIZON_YEARS = 3;

    /** 투영 기간을 3/5로 정규화한다(null 또는 3/5 외 값은 기본 3). */
    public int resolvedHorizonYears() {
        if (horizonYears != null && (horizonYears == 3 || horizonYears == 5)) {
            return horizonYears;
        }
        return DEFAULT_HORIZON_YEARS;
    }
}
