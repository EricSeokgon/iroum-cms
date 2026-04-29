package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 역할 상세 응답 DTO (권한 코드 목록 포함).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — GET /api/v1/roles/{code}, POST/PUT /api/v1/roles 응답.
 *
 * @param code            역할 코드
 * @param name            역할 표시명
 * @param description     역할 설명
 * @param isSystem        시스템 기본 역할 여부
 * @param aliasedTo       Alias 대상 역할 코드
 * @param userCount       해당 역할 보유 사용자 수
 * @param permissionCodes 역할에 매핑된 권한 코드 집합 (alias 시 실제 역할의 권한)
 * @param createdAt       생성 시각
 */
public record RoleDetail(
        String code,
        String name,
        String description,
        boolean isSystem,
        String aliasedTo,
        int userCount,
        Set<String> permissionCodes,
        Instant createdAt
) {}
