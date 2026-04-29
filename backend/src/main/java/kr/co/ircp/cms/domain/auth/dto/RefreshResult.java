package kr.co.ircp.cms.domain.auth.dto;

/**
 * Refresh Token 갱신 결과 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-002 — Refresh Token Rotation.
 * 새 Access Token은 응답 본문으로, 새 Refresh Token은 Set-Cookie로 전달.
 */
public record RefreshResult(

    /** 새 JWT Access Token */
    String accessToken,

    /** 새 Refresh Token 값 (Set-Cookie 헤더에 설정할 원본 값) */
    String newRefreshToken,

    /** Access Token 유효 시간 (초) */
    long accessExpiresInSeconds,

    /** Refresh Token 유효 시간 (초) */
    long refreshExpiresInSeconds
) {}
