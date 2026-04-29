package kr.co.ircp.cms.domain.auth.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Set;

/**
 * 역할-권한 매핑 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013-D-4 — role_permissions 테이블 접근.
 * SQL은 mybatis/mapper/auth/RolePermissionMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] RolePermissionMapper — 역할별 권한 매핑 DB 접근의 핵심 계층
// @MX:REASON: PermissionService, RoleService, AuthServiceImpl 등 fan_in >= 3 참조
@Mapper
public interface RolePermissionMapper {

    /**
     * 역할에 직접 매핑된 권한 코드 집합 조회.
     *
     * @param roleCode 역할 코드
     */
    Set<String> findPermissionCodesByRole(@Param("roleCode") String roleCode);

    /**
     * 실질 권한 코드 집합 조회 (alias 처리 포함).
     *
     * <p>aliased_to가 NOT NULL인 역할은 aliased_to 역할의 권한을 반환.
     * alias 아닌 경우 직접 매핑된 권한 반환.
     *
     * @param roleCode 역할 코드 (alias 포함)
     */
    Set<String> findEffectivePermissionCodes(@Param("roleCode") String roleCode);

    /**
     * 역할-권한 매핑 일괄 삽입.
     *
     * <p>기존 매핑과 충돌 시 무시 (ON CONFLICT DO NOTHING).
     *
     * @param roleCode        역할 코드
     * @param permissionCodes 권한 코드 집합
     * @param grantedBy       부여자 userId (null 허용)
     * @param now             부여 시각
     */
    void insertBatch(
            @Param("roleCode") String roleCode,
            @Param("permissionCodes") Set<String> permissionCodes,
            @Param("grantedBy") Long grantedBy,
            @Param("now") Instant now);

    /**
     * 역할의 모든 권한 매핑 삭제.
     *
     * <p>역할 권한 재설정 시 delete + insertBatch 패턴으로 사용.
     *
     * @param roleCode 역할 코드
     */
    void deleteByRole(@Param("roleCode") String roleCode);

    /**
     * 특정 권한 코드를 사용하는 역할 수 조회.
     *
     * <p>권한 삭제 전 참조 여부 확인용.
     *
     * @param permissionCode 권한 코드
     */
    int countByPermission(@Param("permissionCode") String permissionCode);
}
