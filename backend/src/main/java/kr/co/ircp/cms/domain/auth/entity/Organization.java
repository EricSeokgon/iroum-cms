package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 부서·조직 도메인 엔티티 (MyBatis POJO, JPA-free).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014-D-1 — organization 테이블 매핑.
 * DDL 정의는 V5__organization_schema.sql 참조.
 * materialized path 패턴: /{id}/{id}/...
 */
@Data
@Builder
public class Organization {

    /** 내부 기본키 (BIGSERIAL) */
    private Long id;

    /** 조직 코드 (UNIQUE, 변경 불가 권장) */
    private String code;

    /** 조직명 */
    private String name;

    /** 조직 설명 */
    private String description;

    /** 부모 조직 PK (NULL이면 루트) */
    private Long parentId;

    /** 트리 깊이 (루트=0, 최대 5) */
    private Integer depth;

    /**
     * Materialized path.
     *
     * <p>/{id}/{id}/... 형식. 자손 일괄 조회 시 LIKE '경로/%'로 활용.
     */
    private String path;

    /** 동일 depth 내 정렬 순서 */
    private Integer sortOrder;

    /** 조직 상태 */
    private OrganizationStatus status;

    /** 레코드 생성 시각 */
    private Instant createdAt;

    /** 레코드 수정 시각 */
    private Instant updatedAt;

    /** 소프트 삭제 시각 (NULL = 정상) */
    private Instant deletedAt;
}
