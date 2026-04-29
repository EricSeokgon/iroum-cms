package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 본인인증 시도 이력 엔티티.
 *
 * <p>REQ-AUTH-017-D-5 — verification_history 테이블 매핑.
 * IP 기반 부정 시도 차단(시간당 10회) 및 감사 목적으로 사용.
 */
@Getter
@Setter
@NoArgsConstructor
@Builder
public class VerificationHistory {

    /** PK (BIGSERIAL) */
    private Long id;

    /** 인증 대상 (이메일 주소) */
    private String target;

    /** 인증 채널 */
    private String channel;

    /** 인증 목적 */
    private String purpose;

    /** 성공 여부 */
    private boolean success;

    /** 실패 사유 (실패 시에만) */
    private String failureReason;

    /** 요청자 IP 해시 (SHA-256) */
    private String requesterIpHash;

    /** 요청자 User-Agent */
    private String userAgent;

    /** 발생 시각 */
    private Instant occurredAt;

    public VerificationHistory(Long id, String target, String channel, String purpose,
            boolean success, String failureReason, String requesterIpHash,
            String userAgent, Instant occurredAt) {
        this.id = id;
        this.target = target;
        this.channel = channel;
        this.purpose = purpose;
        this.success = success;
        this.failureReason = failureReason;
        this.requesterIpHash = requesterIpHash;
        this.userAgent = userAgent;
        this.occurredAt = occurredAt;
    }
}
