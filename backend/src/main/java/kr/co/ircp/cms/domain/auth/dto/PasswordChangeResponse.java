package kr.co.ircp.cms.domain.auth.dto;

/**
 * 비밀번호 변경 성공 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-009 — 변경 완료 안내 메시지.
 * HTTP 200 응답 바디.
 */
public record PasswordChangeResponse(

        /**
         * 변경 완료 메시지 (예: "비밀번호가 변경되었습니다. 다시 로그인해 주세요.").
         */
        String message
) {}
