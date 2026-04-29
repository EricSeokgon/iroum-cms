package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;

/**
 * SMS OTP 발송 프로바이더 인터페이스.
 *
 * <p>REQ-AUTH-017 Q-1 사용자 결정(2026-04-29): SMS 채널은 v0.4+에서 구현 예정.
 * 현재 구현체(NoOpSmsProvider)는 UnsupportedOperationException을 던진다.
 */
public interface SmsProvider {

    /**
     * SMS OTP 발송.
     *
     * @param phoneNumber 수신 전화번호
     * @param code        6자리 OTP 코드
     * @param purpose     인증 목적
     */
    void sendOtp(String phoneNumber, String code, VerificationPurpose purpose);
}
