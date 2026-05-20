package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;

/**
 * 권한 변경 이력 조회 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016 — API 응답용 레코드.
 * Optional 필드(targetUserId, targetUsername, changedBy, changedByUsername, reason)는 null 허용.
 *
 * @param id               이력 ID
 * @param targetUserId     대상 사용자 ID (역할 부여/회수 시)
 * @param targetUsername   대상 사용자명 (역할 부여/회수 시)
 * @param changeType       변경 유형 (ROLE_ASSIGN 등)
 * @param targetResource   대상 리소스 (역할 코드 또는 권한 코드)
 * @param changedBy        변경 수행자 ID
 * @param changedByUsername 변경 수행자명
 * @param changedAt        변경 시각
 * @param reason           변경 사유
 */
public record PermissionChangeEntry(
        Long id,
        Long targetUserId,
        String targetUsername,
        String changeType,
        String targetResource,
        Long changedBy,
        String changedByUsername,
        Instant changedAt,
        String reason
) {}
