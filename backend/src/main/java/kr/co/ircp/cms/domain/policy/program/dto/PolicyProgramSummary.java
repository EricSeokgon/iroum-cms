package kr.co.ircp.cms.domain.policy.program.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/v1/policy/programs 목록 응답. */
public record PolicyProgramSummary(
        Long id,
        String code,
        String ministry,
        String programName,
        List<String> targetIndustries,
        List<String> targetRegions,
        Instant applicationStart,
        Instant applicationEnd,
        String status
) {}
