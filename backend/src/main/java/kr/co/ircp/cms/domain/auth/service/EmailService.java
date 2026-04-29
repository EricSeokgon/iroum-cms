package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;

/**
 * 이메일 발송 서비스 인터페이스.
 *
 * <p>REQ-AUTH-017-D-1 — OTP 발송 및 비밀번호 재설정 완료 안내 이메일.
 */
public interface EmailService {

    /**
     * OTP 코드 이메일 발송.
     *
     * @param to      수신 이메일 주소
     * @param code    6자리 OTP 코드
     * @param purpose 인증 목적 (제목/본문 맞춤)
     */
    void sendOtp(String to, String code, VerificationPurpose purpose);

    /**
     * 비밀번호 재설정 완료 안내 이메일 발송.
     *
     * @param to 수신 이메일 주소
     */
    void sendPasswordResetNotice(String to);
}
