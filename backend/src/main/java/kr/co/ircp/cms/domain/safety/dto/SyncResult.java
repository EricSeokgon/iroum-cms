package kr.co.ircp.cms.domain.safety.dto;

/**
 * 외부 동기화 결과 요약.
 * REQ-SAFETY-001-D-1: KOSHA OpenAPI / 사고백서 동기화
 */
public record SyncResult(
        int added,
        int updated,
        int failed,
        String message
) {}
