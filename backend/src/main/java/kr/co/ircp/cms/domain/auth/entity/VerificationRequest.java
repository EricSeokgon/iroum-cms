package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 본인인증 요청 엔티티.
 *
 * <p>REQ-AUTH-017-D-1 — verification_request 테이블 매핑.
 * 채널(EMAIL), 대상(email 주소), 목적(PURPOSE), BCrypt(12) 코드 해시를 관리한다.
 */
@Getter
@Setter
@NoArgsConstructor
@Builder
public class VerificationRequest {

    /** PK (BIGSERIAL) */
    private Long id;

    /** 공개용 요청 식별자 (UUID) — 클라이언트에 노출 */
    private UUID requestId;

    /** 인증 채널 (현재 EMAIL만 지원, Q-1) */
    private VerificationChannel channel;

    /** 인증 대상 (이메일 주소) */
    private String target;

    /** 인증 목적 */
    private VerificationPurpose purpose;

    /** OTP 코드 BCrypt 해시 (strength=12) */
    private String codeHash;

    /** 요청 생성 시각 */
    private Instant createdAt;

    /** 만료 시각 (생성 후 5분) */
    private Instant expiresAt;

    /** 현재 시도 횟수 */
    private int attempts;

    /** 최대 허용 시도 횟수 */
    private int maxAttempts;

    /** 인증 상태 */
    private VerificationStatus status;

    /** 인증 성공 시각 */
    private Instant verifiedAt;

    /**
     * 검증 성공 시 발급되는 단기 토큰 (64자 random hex).
     * 비밀번호 재설정 등 후속 작업에서 사용 (유효 5분).
     */
    private String verifiedToken;

    /** 요청자 IP 해시 (SHA-256, IP 차단용) */
    private String requesterIpHash;

    /** 요청자 User-Agent */
    private String userAgent;

    public VerificationRequest(Long id, UUID requestId, VerificationChannel channel,
            String target, VerificationPurpose purpose, String codeHash,
            Instant createdAt, Instant expiresAt, int attempts, int maxAttempts,
            VerificationStatus status, Instant verifiedAt, String verifiedToken,
            String requesterIpHash, String userAgent) {
        this.id = id;
        this.requestId = requestId;
        this.channel = channel;
        this.target = target;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
        this.status = status;
        this.verifiedAt = verifiedAt;
        this.verifiedToken = verifiedToken;
        this.requesterIpHash = requesterIpHash;
        this.userAgent = userAgent;
    }
}
