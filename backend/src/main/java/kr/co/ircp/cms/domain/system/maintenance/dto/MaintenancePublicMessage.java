package kr.co.ircp.cms.domain.system.maintenance.dto;

/**
 * 점검 중 사용자에게 반환되는 공개 메시지 DTO.
 *
 * <p>REQ-SYSTEM-005-D — MaintenanceFilter 503 응답 본문
 */
public record MaintenancePublicMessage(
        String messageKo,
        String messageEn,
        Long retryAfterEpoch
) {}
