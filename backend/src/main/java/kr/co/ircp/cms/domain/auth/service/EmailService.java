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

    /**
     * 가입 승인 확정 안내 이메일 발송.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-017 — USER_APPROVAL_CONFIRMED 템플릿 렌더링 발송.
     * 발송 실패는 예외를 전파하지 않고 로그만 남긴다(REQ-UA-019 graceful fallback).
     *
     * @param to       수신 이메일 주소
     * @param userName 사용자 이름 (템플릿 변수 userName)
     */
    void sendApprovalConfirmed(String to, String userName);

    /**
     * 가입 거절 안내 이메일 발송.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-018 — USER_APPROVAL_REJECTED 템플릿에 거절 사유 주입.
     * 발송 실패는 예외를 전파하지 않고 로그만 남긴다(REQ-UA-019 graceful fallback).
     *
     * @param to              수신 이메일 주소
     * @param userName        사용자 이름 (템플릿 변수 userName)
     * @param rejectionReason 거절 사유 (템플릿 변수 rejectionReason)
     */
    void sendApprovalRejected(String to, String userName, String rejectionReason);
}
