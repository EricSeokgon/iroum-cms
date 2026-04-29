package kr.co.ircp.cms.domain.auth.entity;

/**
 * 본인인증 요청 상태 열거형.
 *
 * <p>REQ-AUTH-017-D-1 — verification_request.status 컬럼 값과 1:1 매핑.
 */
public enum VerificationStatus {
    /** 인증 대기 중 (코드 발송 완료, 아직 검증 안 됨) */
    PENDING,
    /** 인증 성공 (verifiedToken 발급됨) */
    VERIFIED,
    /** 만료됨 (expires_at 초과) */
    EXPIRED,
    /** 실패 확정 (최대 시도 횟수 초과) */
    FAILED
}
