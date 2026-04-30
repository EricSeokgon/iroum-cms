package kr.co.ircp.cms.domain.system.code.dto;

import java.util.List;
import java.util.Map;

/**
 * 공통코드 벌크 조회 응답 DTO.
 *
 * <p>REQ-SYSTEM-004-D — GET /api/v1/system/codes/bulk?groups=A,B,C
 * Map<groupCode, List<CodeResponse>> 형태로 반환
 */
public record BulkCodesResponse(
        Map<String, List<CodeResponse>> codes
) {}
