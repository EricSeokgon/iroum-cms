package kr.co.ircp.cms.domain.auth.security;

import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.exception.AccessOutOfScopeException;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEPT_ADMIN 권한 범위 제한 서비스.
 *
 * <p>SPEC-CMS-002 Q-24 — DEPT_ADMIN은 자기 부서·자손 부서의 사용자/조직만 관리 가능.
 * SUPER_ADMIN은 전체 범위 접근 허용.
 */
// @MX:ANCHOR: [AUTO] PermissionScopeService — Q-24 DEPT_ADMIN 범위 제한의 핵심 서비스
// @MX:REASON: UserServiceImpl, OrganizationServiceImpl, 테스트 등 fan_in >= 3 참조
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionScopeService {

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;

    /**
     * 액터가 대상 사용자에 대한 관리 권한이 있는지 확인.
     *
     * <p>SUPER_ADMIN이면 항상 true.
     * DEPT_ADMIN이면 대상 사용자가 자기 부서 또는 자손 부서에 속할 때만 true.
     *
     * @param actorPrincipal 처리자 Principal
     * @param targetUserId   대상 사용자 PK
     * @return 접근 가능 여부
     */
    public boolean canAccessUser(JwtPrincipal actorPrincipal, long targetUserId) {
        // SUPER_ADMIN은 전체 허용
        if (actorPrincipal.roles().contains("SUPER_ADMIN")) {
            return true;
        }
        // DEPT_ADMIN만 범위 검사
        if (!actorPrincipal.roles().contains("DEPT_ADMIN")) {
            return false;
        }

        String actorOrgPath = getActorOrgPath(actorPrincipal.userId());
        if (actorOrgPath == null) {
            return false;
        }

        User targetUser = userMapper.findById(targetUserId).orElse(null);
        if (targetUser == null || targetUser.getOrganizationId() == null) {
            return false;
        }

        Organization targetOrg = organizationMapper.findById(targetUser.getOrganizationId()).orElse(null);
        if (targetOrg == null) {
            return false;
        }

        return targetOrg.getPath().startsWith(actorOrgPath);
    }

    /**
     * 액터가 대상 조직에 대한 관리 권한이 있는지 확인.
     *
     * @param actorPrincipal 처리자 Principal
     * @param orgId          대상 조직 PK
     * @return 접근 가능 여부
     */
    public boolean canAccessOrganization(JwtPrincipal actorPrincipal, long orgId) {
        if (actorPrincipal.roles().contains("SUPER_ADMIN")) {
            return true;
        }
        if (!actorPrincipal.roles().contains("DEPT_ADMIN")) {
            return false;
        }

        String actorOrgPath = getActorOrgPath(actorPrincipal.userId());
        if (actorOrgPath == null) {
            return false;
        }

        Organization targetOrg = organizationMapper.findById(orgId).orElse(null);
        if (targetOrg == null) {
            return false;
        }

        return targetOrg.getPath().startsWith(actorOrgPath);
    }

    /**
     * 대상 사용자 접근 권한을 검증하고, 없으면 AccessOutOfScopeException 발생.
     *
     * @param actorPrincipal 처리자 Principal
     * @param targetUserId   대상 사용자 PK
     * @throws AccessOutOfScopeException 범위 외 접근 시도
     */
    public void requireUserAccess(JwtPrincipal actorPrincipal, long targetUserId) {
        if (!canAccessUser(actorPrincipal, targetUserId)) {
            throw new AccessOutOfScopeException("User", targetUserId);
        }
    }

    /**
     * 대상 조직 접근 권한을 검증하고, 없으면 AccessOutOfScopeException 발생.
     *
     * @param actorPrincipal 처리자 Principal
     * @param orgId          대상 조직 PK
     * @throws AccessOutOfScopeException 범위 외 접근 시도
     */
    public void requireOrganizationAccess(JwtPrincipal actorPrincipal, long orgId) {
        if (!canAccessOrganization(actorPrincipal, orgId)) {
            throw new AccessOutOfScopeException("Organization", orgId);
        }
    }

    // ─── 내부 헬퍼 ───────────────────────────────────────────

    private String getActorOrgPath(long actorId) {
        User actor = userMapper.findById(actorId).orElse(null);
        if (actor == null || actor.getOrganizationId() == null) {
            return null;
        }
        Organization actorOrg = organizationMapper.findById(actor.getOrganizationId()).orElse(null);
        return actorOrg != null ? actorOrg.getPath() : null;
    }
}
