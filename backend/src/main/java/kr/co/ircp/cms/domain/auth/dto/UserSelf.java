package kr.co.ircp.cms.domain.auth.dto;

import java.util.Set;

/**
 * 본인 정보 조회 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET /api/v1/me 응답.
 * 로그인한 사용자 본인만 조회 가능. 민감 정보(비밀번호 이력, 잠금 정보) 제외.
 */
public record UserSelf(
        long id,
        String uuid,
        String username,
        String email,
        String name,
        Set<String> roleCodes
) {}
