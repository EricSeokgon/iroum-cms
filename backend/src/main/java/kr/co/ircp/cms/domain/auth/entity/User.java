package kr.co.ircp.cms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자 마스터 도메인 엔티티 (MyBatis POJO, JPA-free).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~006 범위의 사용자 정보를 담는다.
 * DDL 정의는 V2__auth_schema.sql §users 참조.
 *
 * <p>{@code @NoArgsConstructor} + {@code @AllArgsConstructor}: MyBatis 가
 * 컬럼 순서 기반 constructor automapping 으로 잘못된 슬롯에 값을 주입하지 않도록
 * 명시적으로 무인자 생성자를 제공한다 (SPEC-CMS-SECURITY-PII-001 V24 적용 후
 * 신규 PII 필드가 추가되며 발견된 회귀 방지).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** 내부 기본키 (BIGSERIAL) */
    private Long id;

    /** 외부 노출용 UUID (DDL: UUID NOT NULL DEFAULT gen_random_uuid()) */
    private String uuid;

    /** 로그인 ID (unique) */
    private String username;

    /**
     * 이메일 (메모리 평문). V24 적용 후:
     * <ul>
     *   <li>READ 경로: emailEncrypted 가 NULL 이면 평문 그대로, 아니면 EmailEncryptionService.decrypt() 결과를 set</li>
     *   <li>WRITE 경로: 서비스에서 암호화하여 emailEncrypted/Iv/Tag/KeyVersion + emailHmac 컬럼에 저장</li>
     * </ul>
     * V25 평문 컬럼 DROP 이후에는 메모리 전용 평문 캐시로만 동작한다.
     */
    private String email;

    /** AES-256-GCM 암호문 (V24 신규, SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-001) */
    private byte[] emailEncrypted;

    /** GCM IV — 12 bytes (V24 신규) */
    private byte[] emailIv;

    /** GCM authentication tag — 16 bytes (V24 신규) */
    private byte[] emailTag;

    /**
     * PII 암호화 키 버전 (V24 신규, 점진적 회전 지원).
     * INSERT 시 미지정이면 DB DEFAULT 1, UPDATE 시 미지정이면 기존 값 유지(COALESCE).
     */
    private Integer emailKeyVersion;

    /** HMAC-SHA256(hmacKey, normalizedEmail) hex 64 chars — lookup 키 (V24 신규, REQ-PII-EMAIL-003) */
    private String emailHmac;

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

    /** 승인 대기 리마인더 발송 시각(미발송=NULL). SPEC-CMS-USER-APPROVAL-002 REQ-UA2-003 */
    private Instant reminderSentAt;

    /** 이메일 인증 완료 시각(NULL=미인증). SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 */
    private Instant emailVerifiedAt;
}
