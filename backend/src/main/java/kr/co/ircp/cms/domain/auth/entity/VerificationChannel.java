package kr.co.ircp.cms.domain.auth.entity;

/**
 * 본인인증 채널 열거형.
 *
 * <p>REQ-AUTH-017-D-1 — Q-1 사용자 결정(2026-04-29): EMAIL만 지원. SMS는 v0.4+.
 */
public enum VerificationChannel {
    /** 이메일 OTP — 현재 유일하게 지원하는 채널 */
    EMAIL
    // SMS — v0.4+ (Q-1 사용자 결정으로 1차 미적용)
}
