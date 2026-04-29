package kr.co.ircp.cms.domain.auth.entity;

/**
 * 개인정보 접근 목적 코드 열거형.
 *
 * <p>REQ-AUTH-018-D-1 — personal_data_access_log.purpose 컬럼 CHECK 제약과 값이 일치해야 한다.
 * 개인정보보호법 §18 목적 외 이용 추적을 위해 접근 목적을 명시적으로 분류한다.
 */
public enum PersonalDataAccessPurpose {

    /** 업무 문의 처리를 위한 개인정보 조회 */
    BUSINESS_INQUIRY,

    /** 고객 지원을 위한 개인정보 조회 */
    SUPPORT,

    /** 감사·컴플라이언스 목적 조회 */
    AUDIT,

    /** 본인 정보 자기 조회 (REQ-AUTH-018-D-4 본인 접근 이력 노출) */
    SELF_VIEW,

    /** 관리자 사용자 목록 조회 */
    ADMIN_USER_LIST,

    /** 관리자 사용자 정보 수정 */
    ADMIN_USER_EDIT,

    /** 개인정보 내보내기 (Export) */
    EXPORT
}
