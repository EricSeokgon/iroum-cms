package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.dto.RoleCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.RoleDetail;
import kr.co.ircp.cms.domain.auth.dto.RoleSummary;
import kr.co.ircp.cms.domain.auth.dto.RoleUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.Role;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.RoleHasUsersException;
import kr.co.ircp.cms.domain.auth.exception.SystemRoleProtectedException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
import kr.co.ircp.cms.domain.auth.repository.RoleMapper;
import kr.co.ircp.cms.domain.auth.repository.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 역할 마스터 관리 서비스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — 역할 CRUD + 권한 매핑 관리.
 */
// @MX:ANCHOR: [AUTO] RoleService — 역할 CRUD 및 권한 매핑의 핵심 서비스
// @MX:REASON: RoleController, PermissionService, AuthServiceImpl 등 fan_in >= 3 참조
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionService permissionService;
    private final PermissionChangeHistoryService permissionChangeHistoryService;

    /**
     * 전체 역할 목록 조회 (user_count, permission_count 포함).
     */
    @Transactional(readOnly = true)
    public List<RoleSummary> findAll() {
        return roleMapper.findAll();
    }

    /**
     * 역할 코드로 상세 조회 (권한 코드 집합 포함).
     *
     * @param code 역할 코드
     * @return RoleDetail
     * @throws ResponseStatusException 404 — 존재하지 않는 역할
     */
    @Transactional(readOnly = true)
    public RoleDetail findByCode(String code) {
        Role role = roleMapper.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "역할을 찾을 수 없습니다: " + code));
        int userCount = roleMapper.countUsers(code);
        Set<String> permCodes = permissionService.findEffectivePermissionsForRole(code);
        return toDetail(role, userCount, permCodes);
    }

    /**
     * 역할 신규 생성.
     *
     * @param req     생성 요청 DTO
     * @param actorId 생성자 userId
     * @return 생성된 RoleDetail
     * @throws ResponseStatusException 409 — 코드 중복
     */
    @Transactional
    @AuditLog(action = "CREATE", entityType = "Role", captureArgs = false)
    public RoleDetail create(RoleCreateRequest req, long actorId) {
        if (roleMapper.existsByCode(req.code())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "역할 코드가 이미 존재합니다: " + req.code());
        }

        Role role = Role.builder()
                .code(req.code())
                .name(req.name())
                .description(req.description())
                .isSystem(false)
                .aliasedTo(null)
                .build();
        roleMapper.insert(role);

        Set<String> permCodes = req.permissionCodes() != null
                ? req.permissionCodes()
                : Collections.emptySet();
        if (!permCodes.isEmpty()) {
            rolePermissionMapper.insertBatch(req.code(), permCodes, actorId, Instant.now());
        }

        return findByCode(req.code());
    }

    /**
     * 역할 정보 수정.
     *
     * <p>is_system=true인 역할은 name/description만 수정 가능.
     *
     * @param code    역할 코드
     * @param req     수정 요청 DTO
     * @param actorId 수정자 userId
     * @return 수정된 RoleDetail
     */
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "Role")
    public RoleDetail update(String code, RoleUpdateRequest req, long actorId) {
        Role existing = roleMapper.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "역할을 찾을 수 없습니다: " + code));

        Role patch = Role.builder()
                .code(code)
                .name(req.name())
                .description(req.description())
                .build();
        roleMapper.update(patch);

        // 권한 재설정 (null이면 변경 없음, 빈 Set이면 전체 해제)
        if (req.permissionCodes() != null) {
            rolePermissionMapper.deleteByRole(code);
            if (!req.permissionCodes().isEmpty()) {
                rolePermissionMapper.insertBatch(code, req.permissionCodes(), actorId, Instant.now());
            }
        }

        return findByCode(code);
    }

    /**
     * 역할 삭제.
     *
     * @param code    역할 코드
     * @param actorId 삭제자 userId
     * @throws SystemRoleProtectedException is_system=true 역할 삭제 시도 → HTTP 400
     * @throws RoleHasUsersException        사용자 매핑 존재 → HTTP 409
     */
    @Transactional
    @AuditLog(action = "DELETE", entityType = "Role", severity = "WARN")
    public void delete(String code, long actorId) {
        Role role = roleMapper.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "역할을 찾을 수 없습니다: " + code));

        if (role.isSystem()) {
            throw new SystemRoleProtectedException(code);
        }

        int userCount = roleMapper.countUsers(code);
        if (userCount > 0) {
            throw new RoleHasUsersException(code, userCount);
        }

        rolePermissionMapper.deleteByRole(code);
        roleMapper.delete(code);
    }

    /**
     * 역할 권한 재설정 (atomic replace) + diff 기반 이력 적재 (REQ-AUTH-016).
     *
     * @param code            역할 코드
     * @param permissionCodes 새 권한 코드 집합
     * @param actorId         수정자 userId
     */
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "Role")
    public void updatePermissions(String code, Set<String> permissionCodes, long actorId) {
        roleMapper.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "역할을 찾을 수 없습니다: " + code));

        // diff 계산: 기존 권한 vs 새 권한
        Set<String> oldPerms = permissionService.findEffectivePermissionsForRole(code);
        Set<String> newPerms = permissionCodes != null ? permissionCodes : Collections.emptySet();

        Set<String> granted = new HashSet<>(newPerms);
        granted.removeAll(oldPerms);

        Set<String> revoked = new HashSet<>(oldPerms);
        revoked.removeAll(newPerms);

        rolePermissionMapper.deleteByRole(code);
        if (!newPerms.isEmpty()) {
            rolePermissionMapper.insertBatch(code, newPerms, actorId, Instant.now());
        }

        // 이력 적재 (REQ-AUTH-016)
        String reason = "역할 권한 재설정";
        for (String permCode : granted) {
            permissionChangeHistoryService.recordPermissionGrant(code, permCode, actorId, reason);
        }
        for (String permCode : revoked) {
            permissionChangeHistoryService.recordPermissionRevoke(code, permCode, actorId, reason);
        }
    }

    // ─── 내부 변환 헬퍼 ───────────────────────────────────────────

    private RoleDetail toDetail(Role r, int userCount, Set<String> permCodes) {
        return new RoleDetail(
                r.getCode(), r.getName(), r.getDescription(),
                r.isSystem(), r.getAliasedTo(),
                userCount, permCodes, r.getCreatedAt()
        );
    }
}
