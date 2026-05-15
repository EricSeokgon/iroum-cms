package kr.co.ircp.cms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 조직 변경 이력 도메인 엔티티 (MyBatis POJO).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014-D-4 — organization_history 테이블 매핑.
 * 각 CUD 이벤트마다 조직의 전체 상태를 JSONB 스냅샷으로 기록.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationHistory {

    /** 이력 기본키 (BIGSERIAL) */
    private Long id;

    /** 대상 조직 PK */
    private Long orgId;

    /** 조직별 단조 증가 버전 번호 */
    private Integer version;

    /** 변경 시점 organization 행 전체 JSON 문자열 */
    private String snapshot;

    /** 변경 수행자 user_id (NULL이면 시스템) */
    private Long changedBy;

    /** 변경 시각 */
    private Instant changedAt;

    /** 변경 요약 (CREATE/UPDATE/DELETE) */
    private String changeSummary;
}
