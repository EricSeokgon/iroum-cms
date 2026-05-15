package kr.co.ircp.cms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Refresh Token 엔티티 (Rotation 정책 지원).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-002 — 발급된 Refresh Token의 SHA-256 해시를 저장하여
 * 재사용 감지(Token Reuse Detection) 및 회전을 구현한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    /** 기본키 (BIGSERIAL) */
    private Long id;

    /**
     * 토큰 값의 SHA-256 해시 (unique).
     *
     * <p>실제 토큰 값은 저장하지 않는다 — 해시만 보존.
     */
    private String tokenHash;

    /** 토큰 발급 대상 사용자 ID */
    private Long userId;

    /** 토큰 만료 시각 (기본 7일) */
    private Instant expiresAt;

    /** 토큰 회수(revoke) 시각 (NULL=유효) */
    private Instant revokedAt;

    /** 발급 시 클라이언트 IP */
    private String ipAddress;

    /** 발급 시 User-Agent */
    private String userAgent;

    /** 레코드 생성 시각 */
    private Instant createdAt;
}
