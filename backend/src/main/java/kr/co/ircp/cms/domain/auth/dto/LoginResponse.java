package kr.co.ircp.cms.domain.auth.dto;

/**
 * 로그인 성공 응답 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001 — Access Token은 응답 본문으로, Refresh Token은
 * HttpOnly Cookie로 전달 (본 DTO는 바디 부분만 표현).
 */
public record LoginResponse(

    /** JWT Access Token (만료: 15분) */
    String accessToken,

    /** Access Token 유효 시간 (초 단위, 기본 900) */
    long expiresInSeconds,

    /** 토큰 유형 — 항상 "Bearer" */
    String tokenType
) {}
