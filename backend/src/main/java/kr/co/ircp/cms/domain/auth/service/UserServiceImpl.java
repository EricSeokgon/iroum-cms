package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.UserCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSelfUpdateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.dto.UserUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 사용자 CRUD 서비스 구현체.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — 사용자 관리 비즈니스 로직.
 * 트랜잭션 경계: 조회는 readOnly=true, 변경은 기본 트랜잭션.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> VALID_SORT_COLUMNS = Set.of(
            "createdAt,desc", "createdAt,asc",
            "updatedAt,desc", "updatedAt,asc",
            "username,asc", "username,desc",
            "lastLoginAt,desc", "lastLoginAt,asc"
    );

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordPolicyService passwordPolicyService;

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

    @Override
    @Transactional(readOnly = true)
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

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(hashed)
                .name(req.name())
                .status(initialStatus)
                .build();

        userMapper.insert(user);

        // 역할 부여
        Set<String> roleCodes = req.roleCodes() != null ? req.roleCodes() : Collections.emptySet();
        Instant now = Instant.now();
        for (String roleCode : roleCodes) {
            userMapper.insertRole(user.getId(), roleCode, createdBy, now);
        }

        return findById(user.getId());
    }

    @Override
    @Transactional
    @AuditLog(action = "UPDATE", entityType = "User")
    public UserDetail update(long id, UserUpdateRequest req, long updatedBy) {
        // 존재 확인
        userMapper.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        UserStatus newStatus = req.status() != null ? UserStatus.valueOf(req.status()) : null;

        User patch = User.builder()
                .id(id)
                .email(req.email())
                .name(req.name())
                .status(newStatus)
                .build();

        userMapper.update(patch);

        // 역할 재설정 (null이면 변경 없음)
        if (req.roleCodes() != null) {
            userMapper.deleteRolesByUserId(id);
            Instant now = Instant.now();
            for (String roleCode : req.roleCodes()) {
                userMapper.insertRole(id, roleCode, updatedBy, now);
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
    public UserSelf getMe(long currentUserId) {
        User user = userMapper.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));
        Set<String> roles = userMapper.findRoleCodesByUserId(currentUserId);
        return new UserSelf(user.getId(), null, user.getUsername(),
                user.getEmail(), user.getName(), roles);
    }

    @Override
    @Transactional
    public UserSelf updateMe(long currentUserId, UserSelfUpdateRequest req) {
        User existing = userMapper.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));

        // 이메일·이름만 수정 가능 (status·role 제외)
        User patch = User.builder()
                .id(currentUserId)
                .email(req.email())
                .name(req.name())
                .build();
        userMapper.update(patch);

        return getMe(currentUserId);
    }

    // ─── 내부 변환 헬퍼 ───────────────────────────────────────────

    private UserDetail toDetail(User u, Set<String> roles) {
        return new UserDetail(
                u.getId(),
                null,            // uuid 컬럼 — User 엔티티 확장 후 매핑 (현재 RED 단계 유보)
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
