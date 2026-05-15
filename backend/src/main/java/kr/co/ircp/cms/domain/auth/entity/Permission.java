package kr.co.ircp.cms.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 권한(Permission) 카탈로그 도메인 엔티티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013-D-2 — resource:action 형식의 권한 카탈로그.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    /** 권한 코드 (PK, e.g. USER:READ, ROLE:WRITE) */
    private String code;

    /** 리소스 유형 (e.g. USER, ORGANIZATION, ROLE) */
    private String resource;

    /** 액션 유형 (READ/WRITE/DELETE/EXECUTE/ADMIN) */
    private String action;

    /** 권한 설명 */
    private String description;

    /** 레코드 생성 시각 */
    private Instant createdAt;
}
