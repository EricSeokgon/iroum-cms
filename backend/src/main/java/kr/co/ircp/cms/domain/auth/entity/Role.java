package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 역할(Role) 마스터 도메인 엔티티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-007, v0.3.2 Q-4 SYSADMIN alias 정책 적용.
 * aliased_to가 null이 아니면 해당 role_code의 권한 집합을 따른다.
 */
@Data
@Builder
public class Role {

    /** 역할 코드 (PK, e.g. SUPER_ADMIN, EDITOR) */
    private String code;

    /** 역할 표시명 */
    private String name;

    /** 역할 설명 */
    private String description;

    /** 시스템 기본 역할 여부 (삭제 금지) */
    private boolean isSystem;

    /**
     * Alias 대상 역할 코드.
     *
     * <p>NULL=실제 역할. NOT NULL=alias이며, aliased_to 코드의 권한 집합으로 해석된다.
     * 예: SYSADMIN.aliased_to='SUPER_ADMIN' (사용자 결정 Q-4, 2026-04-29).
     */
    private String aliasedTo;

    /** 레코드 생성 시각 */
    private Instant createdAt;
}
