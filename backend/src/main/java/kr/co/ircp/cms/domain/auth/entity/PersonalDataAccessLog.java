package kr.co.ircp.cms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 개인정보 접근 로그 도메인 엔티티 (MyBatis POJO, APPEND-ONLY).
 *
 * <p>REQ-AUTH-018-D-1 — personal_data_access_log 테이블 매핑.
 * 개인정보보호법 §29에 따라 삽입 후 수정·삭제가 DB 트리거로 차단된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDataAccessLog {

    /** 기본키 (BIGSERIAL, APPEND-ONLY) */
    private Long id;

    /** 열람자(뷰어) 사용자 ID */
    private long viewerId;

    /** 열람자 역할 코드 (스냅샷 — 로그 시점의 역할) */
    private String viewerRole;

    /** 피열람자(대상) 사용자 ID */
    private long targetUserId;

    /**
     * 실제 열람된 개인정보 필드 목록 (JSONB 직렬화).
     *
     * <p>예: ["email", "phone", "name"]
     */
    private List<String> accessedFields;

    /** 접근 목적 코드 (REQ-AUTH-018-D-1 CHECK 제약과 일치) */
    private String purpose;

    /** 클라이언트 IP 주소 (IPv4/IPv6) */
    private String ipAddress;

    /** 클라이언트 User-Agent */
    private String userAgent;

    /** 분산 추적 ID (MDC traceId) */
    private String traceId;

    /** 접근 시각 (DB DEFAULT NOW()) */
    private Instant accessedAt;
}
