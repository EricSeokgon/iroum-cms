package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.annotation.PersonalDataAccess;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.UserCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSelfUpdateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.dto.UserUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 사용자 CRUD 서비스 구현체.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — 사용자 관리 비즈니스 로직.
 * 트랜잭션 경계: 조회는 readOnly=true, 변경은 기본 트랜잭션.
 *
 * <p>REQ-AUTH-016: 역할 부여/회수 시 {@link PermissionChangeHistoryService}를 통해 이력 적재.
 */
// @MX:WARN: [AUTO] UserServiceImpl — 역할 diff 계산 포함; 기존 역할 조회 후 추가/제거 비교
// @MX:REASON: update() 내 역할 재설정 시 before/after diff 계산으로 recordRoleAssignment/Unassignment 호출; 목록 크기에 비례한 처리 비용 존재
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> VALID_SORT_COLUMNS = Set.of(
            "createdAt,desc", "createdAt,asc",
            "updatedAt,desc", "updatedAt,asc",
            "username,asc", "username,desc",
            "lastLoginAt,desc", "lastLoginAt,asc"
    );

    private static final String REASON_AUTO_CREATE = "사용자 생성 시 자동 부여";
    private static final String REASON_AUTO_UPDATE = "사용자 수정 시 자동 변경";

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordPolicyService passwordPolicyService;
    private final OrganizationMapper organizationMapper;
    private final PermissionChangeHistoryService permissionChangeHistoryService;
    /**
     * SPEC-CMS-SECURITY-PII-001 V24 — email AES-256-GCM 암호화 + HMAC 격상 서비스.
     * create/update 경로에서 평문 email 을 암호화하여 emailEncrypted/Iv/Tag/Hmac/KeyVersion 컬럼에 저장한다.
     */
    private final EmailEncryptionService emailEncryptionService;

    // @MX:ANCHOR: [AUTO] findPage — 사용자 목록 API 진입점, UserController.list 호출
    // @MX:REASON: 페이징·검색·정렬 복합 쿼리; sort 파라미터 화이트리스트 검증 포함 (fan_in >= 3)
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> findPage(int page, int size, String sort,
                                              String search, String status) {
        // sort 화이트리스트 검증 — SQL Injection 방지
        String safeSort = VALID_SORT_COLUMNS.contains(sort) ? sort : "createdAt,desc";
        int offset = page * size;

        List<UserSummary> content = userMapper.findPage(offset, size, search, status, safeSort);
        long total = userMapper.countAll(search, status);

        return PageResponse.of(content, page, size, total);
    }

    // @MX:WARN: [AUTO] findPage(actor) — DEPT_ADMIN 범위 제한 쿼리; 조직 경로 미설정 시 전체 노출 위험
    // @MX:REASON: Q-24 — actor 조직 path가 null이면 orgPathPrefix 필터 미적용, DEPT_ADMIN이 전체 조회 가능해짐
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> findPage(int page, int size, String sort,
                                              String search, String status,
                                              JwtPrincipal actor) {
        String safeSort = VALID_SORT_COLUMNS.contains(sort) ? sort : "createdAt,desc";
        int offset = page * size;

        // SUPER_ADMIN: 전체 조회 (orgPathPrefix 없음)
        // DEPT_ADMIN: 자기 부서·자손 부서 사용자만 조회
        String orgPathPrefix = null;
        boolean isSuperAdmin = actor.roles().contains("SUPER_ADMIN");
        boolean isDeptAdmin = actor.roles().contains("DEPT_ADMIN");

        if (!isSuperAdmin && isDeptAdmin) {
            Long actorOrgId = userMapper.findById(actor.userId())
                    .map(User::getOrganizationId)
                    .orElse(null);
            if (actorOrgId != null) {
                orgPathPrefix = organizationMapper.findById(actorOrgId)
                        .map(Organization::getPath)
                        .orElse(null);
            }
        }

        List<UserSummary> content = userMapper.findPageWithScope(offset, size, search, status, safeSort, orgPathPrefix);
        long total = userMapper.countAllWithScope(search, status, orgPathPrefix);

        return PageResponse.of(content, page, size, total);
    }

    // @MX:WARN: [AUTO] findById — @PersonalDataAccess AOP 적재; N+1 주의: detail 조회는 단건이므로 허용
    // @MX:REASON: REQ-AUTH-018 PIA 추적 목적. 대량 list 호출 시에는 AOP 적재 생략 (설계 결정: detail/edit/me만 추적)
    @Override
    @Transactional(readOnly = true)
    @PersonalDataAccess(fields = {"email", "name", "phone"}, purpose = "BUSINESS_INQUIRY", targetUserIdParam = "id")
    public UserDetail findById(long id) {
        User user = userMapper.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        Set<String> roles = userMapper.findRoleCodesByUserId(id);
        return toDetail(user, roles);
    }

    // @MX:ANCHOR: [AUTO] create — 사용자 생성 핵심 로직
    // @MX:REASON: 비밀번호 검증·중복 확인·해싱·역할 부여 복합 트랜잭션; UserController 호출 (fan_in >= 3)
    @Override
    @Transactional
    @AuditLog(action = "CREATE", entityType = "User", captureArgs = false)
    public UserDetail create(UserCreateRequest req, long createdBy) {
        // 비밀번호 정책 검증 (8자, 3종류 이상)
        passwordPolicyService.validate(req.password());

        // 중복 검사
        if (userMapper.existsByUsername(req.username())) {
            throw new DuplicateUserException("username", req.username());
        }
        if (userMapper.existsByEmail(req.email())) {
            throw new DuplicateUserException("email", req.email());
        }

        // BCrypt 해싱 후 저장
        String hashed = passwordPolicyService.hash(req.password());
        UserStatus initialStatus = req.status() != null
                ? UserStatus.valueOf(req.status())
                : UserStatus.ACTIVE;

        // SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-001/003 — email AES-256-GCM 암호화 + HMAC 격상
        EncryptedEmail encryptedEmail = emailEncryptionService.encrypt(req.email());
        String emailHmac = emailEncryptionService.computeHmac(req.email());

        User user = User.builder()
                .username(req.username())
                .email(req.email())                                  // 메모리 평문 (V25 전까지 컬럼도 평문)
                .passwordHash(hashed)
                .name(req.name())
                .status(initialStatus)
                .emailEncrypted(encryptedEmail.ciphertext())
                .emailIv(encryptedEmail.iv())
                .emailTag(encryptedEmail.tag())
                .emailKeyVersion(encryptedEmail.keyVersion())
                .emailHmac(emailHmac)
                .build();

        userMapper.insert(user);

        // 역할 부여 + 이력 적재 (REQ-AUTH-016)
        Set<String> roleCodes = req.roleCodes() != null ? req.roleCodes() : Collections.emptySet();
        Instant now = Instant.now();
        for (String roleCode : roleCodes) {
            userMapper.insertRole(user.getId(), roleCode, createdBy, now);
            permissionChangeHistoryService.recordRoleAssignment(user.getId(), roleCode, createdBy, REASON_AUTO_CREATE);
        }

        return findById(user.getId());
    }

    @Override
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "User")
    @PersonalDataAccess(fields = {"email", "name"}, purpose = "ADMIN_USER_EDIT", targetUserIdParam = "id")
    public UserDetail update(long id, UserUpdateRequest req, long updatedBy) {
        // 존재 확인
        userMapper.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        UserStatus newStatus = req.status() != null ? UserStatus.valueOf(req.status()) : null;

        // SPEC-CMS-SECURITY-PII-001 — email 변경 시 신규 암호화 + 신규 HMAC 적재
        User.UserBuilder patchBuilder = User.builder()
                .id(id)
                .email(req.email())
                .name(req.name())
                .status(newStatus);
        if (req.email() != null) {
            EncryptedEmail encryptedEmail = emailEncryptionService.encrypt(req.email());
            patchBuilder
                    .emailEncrypted(encryptedEmail.ciphertext())
                    .emailIv(encryptedEmail.iv())
                    .emailTag(encryptedEmail.tag())
                    .emailKeyVersion(encryptedEmail.keyVersion())
                    .emailHmac(emailEncryptionService.computeHmac(req.email()));
        }
        User patch = patchBuilder.build();

        userMapper.update(patch);

        // 역할 재설정 (null이면 변경 없음) + diff 기반 이력 적재 (REQ-AUTH-016)
        if (req.roleCodes() != null) {
            Set<String> oldRoles = userMapper.findRoleCodesByUserId(id);
            Set<String> newRoles = req.roleCodes();

            // 추가된 역할
            Set<String> added = new HashSet<>(newRoles);
            added.removeAll(oldRoles);

            // 제거된 역할
            Set<String> removed = new HashSet<>(oldRoles);
            removed.removeAll(newRoles);

            userMapper.deleteRolesByUserId(id);
            Instant now = Instant.now();
            for (String roleCode : newRoles) {
                userMapper.insertRole(id, roleCode, updatedBy, now);
            }

            for (String roleCode : added) {
                permissionChangeHistoryService.recordRoleAssignment(id, roleCode, updatedBy, REASON_AUTO_UPDATE);
            }
            for (String roleCode : removed) {
                permissionChangeHistoryService.recordRoleUnassignment(id, roleCode, updatedBy, REASON_AUTO_UPDATE);
            }
        }

        return findById(id);
    }

    // @MX:WARN: [AUTO] delete — 소프트 삭제: status·deleted_at 동시 갱신 필수
    // @MX:REASON: UserMapper.softDelete가 두 컬럼을 원자적으로 갱신해야 조회 쿼리와 일관성 유지
    @Override
    @Transactional
    @AuditLog(action = "DELETE", entityType = "User")
    public void delete(long id, long deletedBy) {
        userMapper.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userMapper.softDelete(id, Instant.now());
    }

    @Override
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "User", severity = "WARN")
    public void unlock(long id, long actorId) {
        userMapper.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userMapper.unlock(id, Instant.now());
    }

    @Override
    @Transactional
    @AuditLog(action = "PERMISSION_CHANGE", entityType = "User", severity = "WARN")
    public void forceLogout(long id, long actorId) {
        userMapper.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        refreshTokenMapper.revokeAllForUser(id, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    @PersonalDataAccess(fields = {"email", "phone"}, purpose = "SELF_VIEW",
                        targetUserIdParam = "currentUserId", selfAccessOnly = true)
    public UserSelf getMe(long currentUserId) {
        User user = userMapper.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));
        Set<String> roles = userMapper.findRoleCodesByUserId(currentUserId);
        return new UserSelf(user.getId(), user.getUuid(), user.getUsername(),
                user.getEmail(), user.getName(), roles);
    }

    @Override
    @Transactional
    public UserSelf updateMe(long currentUserId, UserSelfUpdateRequest req) {
        User existing = userMapper.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));

        // 이메일·이름만 수정 가능 (status·role 제외)
        // SPEC-CMS-SECURITY-PII-001 — email 변경 시 신규 암호화 + HMAC 적재
        User.UserBuilder patchBuilder = User.builder()
                .id(currentUserId)
                .email(req.email())
                .name(req.name());
        if (req.email() != null) {
            EncryptedEmail encryptedEmail = emailEncryptionService.encrypt(req.email());
            patchBuilder
                    .emailEncrypted(encryptedEmail.ciphertext())
                    .emailIv(encryptedEmail.iv())
                    .emailTag(encryptedEmail.tag())
                    .emailKeyVersion(encryptedEmail.keyVersion())
                    .emailHmac(emailEncryptionService.computeHmac(req.email()));
        }
        userMapper.update(patchBuilder.build());

        return getMe(currentUserId);
    }

    // ─── 내부 변환 헬퍼 ───────────────────────────────────────────

    private UserDetail toDetail(User u, Set<String> roles) {
        return new UserDetail(
                u.getId(),
                u.getUuid(),
                u.getUsername(),
                u.getEmail(),
                u.getName(),
                u.getStatus() != null ? u.getStatus().name() : null,
                u.getFailCount(),
                u.getLockedUntil(),
                u.getLastLoginAt(),
                u.getPasswordChangedAt(),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                roles
        );
    }
}
