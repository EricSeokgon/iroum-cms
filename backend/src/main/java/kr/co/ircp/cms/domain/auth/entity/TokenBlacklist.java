package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Access Token 블랙리스트 엔티티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-003 — 로그아웃 시 아직 만료되지 않은 Access Token의
 * SHA-256 해시를 블랙리스트에 등록하여 재사용을 방지한다.
 */
@Data
@Builder
public class TokenBlacklist {

    /**
     * Access Token의 SHA-256 해시 (PK).
     *
     * <p>실제 JWT 문자열은 저장하지 않는다.
     */
    private String tokenHash;

    /** 토큰 회수 시각 */
    private Instant revokedAt;

    /** 원본 토큰 만료 시각 (GC 기준점) */
    private Instant expiresAt;
}
