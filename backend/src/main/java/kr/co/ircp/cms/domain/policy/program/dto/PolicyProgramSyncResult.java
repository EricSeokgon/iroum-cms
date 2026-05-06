package kr.co.ircp.cms.domain.policy.program.dto;

import java.time.Instant;

/** 외부 OpenAPI 동기화 결과 (mock). */
public record PolicyProgramSyncResult(
        String sourceCode,
        int fetched,
        int inserted,
        int updated,
        int skipped,
        Instant syncedAt
) {}
