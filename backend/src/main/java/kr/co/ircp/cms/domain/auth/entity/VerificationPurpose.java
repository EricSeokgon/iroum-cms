package kr.co.ircp.cms.domain.auth.entity;

/**
 * 본인인증 목적 열거형.
 *
 * <p>REQ-AUTH-017-D-1 — 인증 요청의 목적을 구분하여 verifiedToken의 사용 범위를 제한한다.
 */
public enum VerificationPurpose {
    /** 회원가입 시 이메일 확인 */
    SIGNUP,
    /** 비밀번호 재설정 */
    PASSWORD_RESET,
    /** 중요 정보 변경 (이메일, 휴대폰 등) */
    IMPORTANT_CHANGE
}
