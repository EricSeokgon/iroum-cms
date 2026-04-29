package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 본인 정보 수정 요청 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — PUT /api/v1/me 요청 본문.
 * 이메일과 이름만 수정 가능. 상태·역할 변경은 관리자 전용 API 사용.
 */
public record UserSelfUpdateRequest(

        /** 이메일 (RFC 5322 형식, null이면 변경 없음) */
        @Email
        String email,

        /** 사용자 실명 (100자 이내, null이면 변경 없음) */
        @Size(max = 100)
        String name
) {}
