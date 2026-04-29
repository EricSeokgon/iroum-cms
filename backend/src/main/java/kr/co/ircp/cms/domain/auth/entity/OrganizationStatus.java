package kr.co.ircp.cms.domain.auth.entity;

/**
 * 조직 상태 열거형.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — DDL CHECK 제약 (ACTIVE/INACTIVE/DELETED)과 동기화.
 */
public enum OrganizationStatus {

    /** 정상 운영 중인 조직 */
    ACTIVE,

    /** 비활성화된 조직 (자손·사용자 이동 후 비활성) */
    INACTIVE,

    /** 소프트 삭제 상태 */
    DELETED
}
