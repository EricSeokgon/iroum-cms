package kr.co.ircp.cms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 로그인 이력 엔티티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 성공/실패 로그인 시도를 모두 기록한다.
 * userId는 사용자 미존재 시 NULL 허용 (익명 실패 기록).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginHistory {

    /** 기본키 (BIGSERIAL) */
    private Long id;

    /** 로그인 대상 사용자 ID (사용자 미존재 시 NULL) */
    private Long userId;

    /** 시도한 username (사용자 미존재 케이스 기록용) */
    private String username;

    /** 클라이언트 IP 주소 */
    private String ipAddress;

    /** 클라이언트 User-Agent */
    private String userAgent;

    /** 로그인 성공 여부 */
    private boolean success;

    /**
     * 실패 사유 코드.
     *
     * <p>예: INVALID_PASSWORD, USER_NOT_FOUND, ACCOUNT_LOCKED, ACCOUNT_INACTIVE
     */
    private String failureReason;

    /** 이력 생성 시각 */
    private Instant createdAt;
}
