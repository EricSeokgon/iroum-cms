package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 역할-권한 매핑 엔티티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013-D-4 — 역할별 권한 매핑 테이블.
 */
@Data
@Builder
public class RolePermission {

    /** 역할 코드 (FK → roles.code) */
    private String roleCode;

    /** 권한 코드 (FK → permissions.code) */
    private String permissionCode;

    /** 부여 시각 */
    private Instant grantedAt;

    /** 부여자 userId (NULL 허용) */
    private Long grantedBy;
}
