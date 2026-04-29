package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 역할 생성 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — POST /api/v1/roles 요청.
 *
 * @param code            역할 코드 (대문자·언더스코어만, 3~50자)
 * @param name            역할 표시명 (최대 100자)
 * @param description     역할 설명
 * @param permissionCodes 초기 권한 코드 집합 (null이면 빈 집합)
 */
public record RoleCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{3,50}$",
                message = "역할 코드는 대문자와 언더스코어만 허용하며 3~50자이어야 합니다")
        String code,

        @NotBlank @Size(max = 100)
        String name,

        String description,

        Set<String> permissionCodes
) {}
