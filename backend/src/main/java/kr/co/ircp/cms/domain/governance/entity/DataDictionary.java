package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 데이터 표준 사전 엔티티.
 *
 * <p>SPEC-CMS-009 REQ-GOV-001~004: 테이블·컬럼 한글명, 도메인 분류,
 * S-Meta/DA# 호환 메타데이터.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataDictionary {

    private Long id;
    private String tableName;
    private String columnName;
    private String logicalNameKo;
    private String logicalNameEn;
    /** MASTER | TRANSACTION | STATISTICS | LOG */
    private String dataDomain;
    private String dataType;
    private String description;
    private Boolean isPii;
    private Boolean isRequired;
    /** ACTIVE | DEPRECATED | REMOVED */
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
