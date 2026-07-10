package kr.co.ircp.cms.domain.auth.entity;

/**
 * 사용자 계정 상태 열거형.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004/005 — 계정 잠금·비활성화 상태 관리.
 */
public enum UserStatus {

    /** 정상 활성 계정 */
    ACTIVE,

    /** 관리자에 의해 비활성화된 계정 */
    INACTIVE,

    /** 로그인 실패 5회 초과로 30분간 잠긴 계정 (REQ-AUTH-005) */
    LOCKED,

    /** 소프트 삭제된 계정 (deleted_at 동시 설정) */
    DELETED,

    /** 가입 승인 대기 계정 (SPEC-CMS-USER-APPROVAL-001 — 승인 게이트 ON 시 신규 공개 가입자) */
    PENDING_APPROVAL
}
