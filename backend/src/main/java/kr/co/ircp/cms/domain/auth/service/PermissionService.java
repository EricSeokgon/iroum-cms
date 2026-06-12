package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PermissionSummary;
import kr.co.ircp.cms.domain.auth.entity.Permission;
import kr.co.ircp.cms.domain.auth.repository.PermissionMapper;
import kr.co.ircp.cms.domain.auth.repository.RolePermissionMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 권한 카탈로그 서비스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — 권한 카탈로그 조회 및 역할별 실질 권한 계산.
 */
// @MX:ANCHOR: [AUTO] PermissionService — 권한 카탈로그 및 실질 권한 계산의 핵심 서비스
// @MX:REASON: RoleService, AuthServiceImpl, JwtAuthenticationFilter 등 fan_in >= 3 참조
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserMapper userMapper;

    /**
     * 전체 권한 목록 조회.
     *
     * @return 권한 요약 DTO 목록 (resource, action 오름차순)
     */
    public List<PermissionSummary> findAll() {
        return permissionMapper.findAll().stream()
                .map(p -> new PermissionSummary(p.getCode(), p.getResource(),
                        p.getAction(), p.getDescription()))
                .collect(Collectors.toList());
    }

    /**
     * 역할의 실질 권한 코드 집합 조회 (alias 처리 포함).
     *
     * <p>aliased_to가 있는 역할(예: SYSADMIN)은 aliased_to 역할(예: SUPER_ADMIN)의 권한 반환.
     *
     * @param roleCode 역할 코드 (alias 포함)
     * @return 실질 권한 코드 집합
     */
    public Set<String> findEffectivePermissionsForRole(String roleCode) {
        return rolePermissionMapper.findEffectivePermissionCodes(roleCode);
    }

    /**
     * 사용자의 모든 역할에 대한 실질 권한 코드 union 집합 조회.
     *
     * <p>REQ-AUTH-013 — JWT 생성 시 permissions 클레임 생성에 사용.
     * 사용자가 여러 역할을 보유한 경우 모든 역할의 권한을 합산.
     *
     * @param userId 사용자 PK
     * @return 실질 권한 코드 합산 집합
     */
    // @MX:WARN: [AUTO] findEffectivePermissionsForUser — N+1 권한 조회 (역할 수 * DB 쿼리)
    // @MX:REASON: 역할 수가 많아지면 조회 횟수 증가. 현재 규모에서는 허용, 트래픽 증가 시 캐싱 도입 필요
    public Set<String> findEffectivePermissionsForUser(long userId) {
        Set<String> roleCodes = userMapper.findRoleCodesByUserId(userId);
        return roleCodes.stream()
                .flatMap(rc -> rolePermissionMapper.findEffectivePermissionCodes(rc).stream())
                .collect(Collectors.toSet());
    }

    /**
     * 사용자의 역할 코드 집합 조회 (alias 포함, 원본 그대로).
     *
     * <p>SPEC-CMS-RBAC-001 REQ-RBAC-003 — GET /api/v1/me/permissions 역할 목록 산출에 사용.
     *
     * @param userId 사용자 PK
     * @return 사용자에게 직접 부여된 역할 코드 집합
     */
    public Set<String> findRoleCodesForUser(long userId) {
        return userMapper.findRoleCodesByUserId(userId);
    }
}
