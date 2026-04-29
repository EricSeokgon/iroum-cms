package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 사용자 마스터 도메인 엔티티 (MyBatis POJO, JPA-free).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~006 범위의 사용자 정보를 담는다.
 * DDL 정의는 V2__auth_schema.sql §users 참조.
 */
@Data
@Builder
public class User {

    /** 내부 기본키 (BIGSERIAL) */
    private Long id;

    /** 로그인 ID (unique) */
    private String username;

    /** 이메일 (AES-256-GCM 암호화 저장 — REQ-CROSS-002, RED 단계에서는 평문) */
    private String email;

    /** BCrypt strength=12 해시 (REQ-AUTH-004) */
    private String passwordHash;

    /** 사용자 실명 */
    private String name;

    /** 계정 상태 (REQ-AUTH-005) */
    private UserStatus status;

    /** 연속 로그인 실패 횟수 (REQ-AUTH-005: 5회 초과 시 잠금) */
    private int failCount;

    /** 계정 잠금 해제 시각 (NULL이면 잠금 없음) */
    private Instant lockedUntil;

    /** 마지막 로그인 성공 시각 */
    private Instant lastLoginAt;

    /** 마지막 비밀번호 변경 시각 (90일 초과 시 만료 경고) */
    private Instant passwordChangedAt;

    /** 레코드 생성 시각 */
    private Instant createdAt;

    /** 레코드 수정 시각 */
    private Instant updatedAt;

    /** 소프트 삭제 시각 (NULL = 정상) */
    private Instant deletedAt;

    /** 소속 조직 PK (NULL이면 미배정 — REQ-AUTH-014-D-2) */
    private Long organizationId;
}
