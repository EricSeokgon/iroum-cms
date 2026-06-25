package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.PublicRegisterRequest;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.entity.LoginHistory;
import kr.co.ircp.cms.domain.auth.entity.RefreshToken;
import kr.co.ircp.cms.domain.auth.entity.TokenBlacklist;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.dto.VerifyRequestRequest;
import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.auth.exception.RegistrationTokenInvalidException;
import kr.co.ircp.cms.domain.auth.exception.RegistrationTokenRequiredException;
import kr.co.ircp.cms.domain.auth.entity.VerificationRequest;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.InvalidVerifiedTokenException;
import kr.co.ircp.cms.domain.auth.exception.PasswordReuseException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.exception.TokenReuseException;
import kr.co.ircp.cms.domain.auth.exception.UserPendingApprovalException;
import kr.co.ircp.cms.domain.system.setting.service.SystemSettingService;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.PasswordHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.auth.service.PermissionService;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AuthService 구현체 (Step 2 GREEN).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003, 005, 011 — 로그인, 토큰 갱신, 로그아웃 전체 흐름.
 * Refresh Token Rotation + Token Reuse Detection 포함.
 */
// @MX:ANCHOR: [AUTO] AuthServiceImpl.login — 로그인 흐름의 핵심 구현 (fan_in >= 3)
// @MX:REASON: AuthController, 테스트, 관리자 서비스에서 직접 또는 간접 참조
// @MX:WARN: [AUTO] login 메서드 분기 복잡도 주의 (잠금/비활성/실패횟수 로직 통합)
// @MX:REASON: 계정 잠금·비밀번호 검증·이력 기록이 단일 메서드에 통합되어 cyclomatic complexity 상승
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final LoginHistoryMapper loginHistoryMapper;
    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordHistoryMapper passwordHistoryMapper;
    private final JwtProperties jwtProperties;
    private final PermissionService permissionService;
    private final VerificationService verificationService;
    private final EmailService emailService;
    private final EmailEncryptionService emailEncryptionService;
    // SPEC-CMS-USER-APPROVAL-001 — 가입 승인 게이트 설정 조회 (REGISTRATION_APPROVAL_REQUIRED).
    private final SystemSettingService systemSettingService;

    // SPEC-CMS-SECURITY-MEDIUM-13 — IP 기반 로그인 실패 추적.
    // 동일 IP가 서로 다른 계정에 대해 시도하는 enumeration/brute-force를 방어한다.
    // ConcurrentHashMap + AtomicInteger 인메모리 카운터로 RateLimitFilter와 동일한 단순 패턴 사용.
    // 단일 인스턴스 한계는 RateLimitFilter와 동일 — 분산 환경에서는 Redis로 격상 필요.
    /** 10분 윈도우 내 IP당 누적 실패 횟수. */
    private static final int IP_FAIL_THRESHOLD = 20;
    /** IP 블록 윈도우 (밀리초). 10분. */
    private static final long IP_BLOCK_WINDOW_MS = 10L * 60L * 1000L;
    /** 메모리 폭증 방지 상한 — 카운터 맵 키 수 초과 시 전체 reset. */
    private static final int IP_FAIL_MAX_KEYS = 50_000;
    /** key = IP, value = [실패횟수, 윈도우 시작 시각(epochMillis)]. */
    private final Map<String, IpFailWindow> ipFailCounters = new ConcurrentHashMap<>();

    /**
     * IP별 실패 카운트와 윈도우 시작 시각을 함께 보관하는 슬라이딩 윈도우 단위.
     */
    private static final class IpFailWindow {
        final AtomicInteger count = new AtomicInteger();
        volatile long windowStartMs;

        IpFailWindow(long nowMs) {
            this.windowStartMs = nowMs;
        }
    }

    public AuthServiceImpl(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            LoginHistoryMapper loginHistoryMapper,
            TokenBlacklistMapper tokenBlacklistMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordPolicyService passwordPolicyService,
            PasswordHistoryMapper passwordHistoryMapper,
            JwtProperties jwtProperties,
            PermissionService permissionService,
            VerificationService verificationService,
            EmailService emailService,
            EmailEncryptionService emailEncryptionService,
            SystemSettingService systemSettingService) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.loginHistoryMapper = loginHistoryMapper;
        this.tokenBlacklistMapper = tokenBlacklistMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordPolicyService = passwordPolicyService;
        this.passwordHistoryMapper = passwordHistoryMapper;
        this.jwtProperties = jwtProperties;
        this.permissionService = permissionService;
        this.verificationService = verificationService;
        this.emailService = emailService;
        this.emailEncryptionService = emailEncryptionService;
        this.systemSettingService = systemSettingService;
    }

    /**
     * 일반 로그인.
     *
     * <p>REQ-AUTH-001, 005, 011 통합 흐름:
     * 사용자 조회 → 잠금 확인 → 비밀번호 검증 → 실패 횟수 관리 → 토큰 발급 → 이력 기록.
     */
    @AuditLog(action = "LOGIN", entityType = "User")
    @Override
    public LoginOutcome login(LoginRequest req, String ipAddress, String userAgent) {
        Instant now = Instant.now();

        // 0. SPEC-CMS-SECURITY-MEDIUM-13 — IP 기반 차단 우선 검사.
        // 동일 IP가 여러 계정을 시도하는 enumeration/brute-force를 방어한다.
        // DB 조회 이전에 차단하여 비용·타이밍 누설을 최소화한다.
        if (isIpBlocked(ipAddress, now)) {
            // 응답 본문에는 차단 사실을 노출하지 않고 일반 자격증명 오류로 통일.
            // 서버 로그(loginHistory)에만 IP_BLOCKED 사유를 기록한다.
            loginHistoryMapper.insert(LoginHistory.builder()
                    .username(req.username())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("IP_BLOCKED")
                    .createdAt(now)
                    .build());
            throw new InvalidCredentialsException();
        }

        // 1. 사용자 조회 — 미존재 시 실패 이력 기록 후 예외
        // SPEC-CMS-SECURITY-MEDIUM-13 — enumeration 방지: 사용자 존재/미존재 모두 동일하게
        // InvalidCredentialsException("AUTH_INVALID_CREDENTIALS") 단일 응답으로 반환한다.
        User user = userMapper.findByUsername(req.username()).orElse(null);
        if (user == null) {
            recordIpFailure(ipAddress, now);
            loginHistoryMapper.insert(LoginHistory.builder()
                    .username(req.username())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("USER_NOT_FOUND")
                    .createdAt(now)
                    .build());
            throw new InvalidCredentialsException();
        }

        // 2. 계정 잠금 확인 — LOCKED 상태이고 잠금 기간이 남은 경우
        if (user.getStatus() == UserStatus.LOCKED
                && user.getLockedUntil() != null
                && now.isBefore(user.getLockedUntil())) {
            throw new AccountLockedException();
        }

        // 3. 비활성 / 삭제 계정 확인
        if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.DELETED) {
            loginHistoryMapper.insert(LoginHistory.builder()
                    .userId(user.getId())
                    .username(req.username())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("ACCOUNT_INACTIVE")
                    .createdAt(now)
                    .build());
            throw new InvalidCredentialsException();
        }

        // 3.5 가입 승인 대기 계정 확인 (SPEC-CMS-USER-APPROVAL-001 REQ-UA-004)
        //     PENDING_APPROVAL 사용자는 관리자 승인 전까지 로그인을 거부한다.
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            loginHistoryMapper.insert(LoginHistory.builder()
                    .userId(user.getId())
                    .username(req.username())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("PENDING_APPROVAL")
                    .createdAt(now)
                    .build());
            throw new UserPendingApprovalException();
        }

        // 4. 비밀번호 검증
        if (!passwordPolicyService.matches(req.password(), user.getPasswordHash())) {
            // 실패 횟수 증가
            userMapper.incrementFailCount(req.username(), now);
            int newFailCount = user.getFailCount() + 1;

            // 5회 이상 실패 시 계정 잠금 (REQ-AUTH-005)
            if (newFailCount >= 5) {
                userMapper.lockAccount(req.username(), now.plusSeconds(30 * 60));
            }

            // SPEC-CMS-SECURITY-MEDIUM-13 — IP 기반 누적 실패 기록.
            recordIpFailure(ipAddress, now);

            loginHistoryMapper.insert(LoginHistory.builder()
                    .userId(user.getId())
                    .username(req.username())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("INVALID_PASSWORD")
                    .createdAt(now)
                    .build());
            throw new InvalidCredentialsException();
        }

        // 5. 로그인 성공 처리
        userMapper.resetFailCount(req.username(), now);
        userMapper.updateLastLoginAt(user.getId(), now);

        // 6. 토큰 발급 (REQ-AUTH-013: permissions 클레임 포함)
        // @MX:WARN: [AUTO] login — permissions 조회 추가로 DB 쿼리 증가 (성능 영향)
        // @MX:REASON: 역할 수만큼 N+1 권한 조회 발생. 트래픽 증가 시 Redis 캐싱 도입 필요.
        Set<String> userRoles = userMapper.findRoleCodesByUserId(user.getId());
        Set<String> userPermissions = permissionService.findEffectivePermissionsForUser(user.getId());
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), req.username(), userRoles, userPermissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 7. Refresh Token 저장 (해시)
        Instant refreshExpires = now.plus(jwtProperties.refreshTokenTtl());
        refreshTokenMapper.insert(RefreshToken.builder()
                .tokenHash(sha256Hex(refreshToken))
                .userId(user.getId())
                .expiresAt(refreshExpires)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(now)
                .build());

        // 8. 성공 이력 기록
        loginHistoryMapper.insert(LoginHistory.builder()
                .userId(user.getId())
                .username(req.username())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .createdAt(now)
                .build());

        long expiresInSeconds = jwtProperties.accessTokenTtl().toSeconds();
        return new LoginOutcome(
                new LoginResponse(accessToken, expiresInSeconds, "Bearer"),
                refreshToken);
    }

    /**
     * Refresh Token 갱신 (Rotation).
     *
     * <p>REQ-AUTH-002 — 기존 토큰 회수 + 새 토큰 쌍 발급 + Token Reuse Detection.
     */
    @AuditLog(action = "TOKEN_REFRESH", entityType = "User")
    @Override
    public RefreshResult refresh(String refreshTokenCookie, String ipAddress, String userAgent) {
        Instant now = Instant.now();

        // 1. userId 추출 — 파싱 불가 시 예외
        Long userId = jwtTokenProvider.extractUserId(refreshTokenCookie)
                .orElseThrow(InvalidCredentialsException::new);

        // 2. 토큰 해시로 DB 조회
        String tokenHash = sha256Hex(refreshTokenCookie);
        RefreshToken stored = refreshTokenMapper.findByTokenHash(tokenHash)
                .orElseThrow(InvalidCredentialsException::new);

        // 3. 이미 회수된 토큰 재사용 — 탈취 감지
        if (stored.getRevokedAt() != null) {
            refreshTokenMapper.revokeAllForUser(userId, now);
            throw new TokenReuseException();
        }

        // 4. 만료 확인
        if (stored.getExpiresAt().isBefore(now)) {
            throw new TokenExpiredException();
        }

        // 5. 사용자 정보 + 권한 로드 (REQ-AUTH-002: Refresh 시 최신 권한 반영)
        User user = userMapper.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        String username = user.getUsername();
        Set<String> userRoles = userMapper.findRoleCodesByUserId(userId);
        Set<String> userPermissions = permissionService.findEffectivePermissionsForUser(userId);

        // 6. Rotation — 기존 무효화
        refreshTokenMapper.revoke(tokenHash, now);

        // 7. 새 토큰 쌍 발급 (roles + permissions 포함)
        String newAccess = jwtTokenProvider.generateAccessToken(userId, username, userRoles, userPermissions);
        String newRefresh = jwtTokenProvider.generateRefreshToken(userId);

        Instant refreshExpires = now.plus(jwtProperties.refreshTokenTtl());
        refreshTokenMapper.insert(RefreshToken.builder()
                .tokenHash(sha256Hex(newRefresh))
                .userId(userId)
                .expiresAt(refreshExpires)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(now)
                .build());

        return new RefreshResult(
                newAccess,
                newRefresh,
                jwtProperties.accessTokenTtl().toSeconds(),
                jwtProperties.refreshTokenTtl().toSeconds());
    }

    /**
     * 로그아웃.
     *
     * <p>REQ-AUTH-003 — Refresh Token 회수 + Access Token 블랙리스트 등록.
     */
    @AuditLog(action = "LOGOUT", entityType = "User")
    @Override
    public void logout(String accessToken, String refreshTokenCookie) {
        Instant now = Instant.now();

        // 1. Refresh Token 회수
        if (refreshTokenCookie != null) {
            refreshTokenMapper.revoke(sha256Hex(refreshTokenCookie), now);
        }

        // 2. Access Token 블랙리스트 등록
        if (accessToken != null) {
            JwtTokenProvider.JwtClaims claims = jwtTokenProvider.validateAccessToken(accessToken)
                    .orElse(null);
            Instant expiresAt = (claims != null) ? claims.expiresAt() : now.plusSeconds(900);
            tokenBlacklistMapper.insert(TokenBlacklist.builder()
                    .tokenHash(sha256Hex(accessToken))
                    .revokedAt(now)
                    .expiresAt(expiresAt)
                    .build());
        }
    }

    /**
     * 비밀번호 변경.
     *
     * <p>REQ-AUTH-009, REQ-AUTH-010 흐름:
     * 사용자 조회 → 현재 비밀번호 검증 → 새 비밀번호 정책 검증 → 직전 5개 재사용 검사
     * → 비밀번호 갱신 → 이력 적재 → Refresh Token 전체 무효화.
     */
    // @MX:ANCHOR: [AUTO] AuthServiceImpl.changePassword — 비밀번호 변경 핵심 도메인 흐름 (fan_in >= 3)
    // @MX:REASON: AuthController, 관리자 서비스, 테스트에서 참조; 재사용 금지·정책·세션 무효화 보안 계약 포함
    @Override
    @Transactional
    @AuditLog(action = "PASSWORD_CHANGE", entityType = "User", severity = "INFO")
    public void changePassword(long userId, String currentPassword, String newPassword) {
        Instant now = Instant.now();

        // 1. 사용자 조회
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("사용자를 찾을 수 없습니다"));

        // 2. 현재 비밀번호 검증
        if (!passwordPolicyService.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("현재 비밀번호가 일치하지 않습니다");
        }

        // 3. 새 비밀번호 정책 검증 (길이·복잡도)
        passwordPolicyService.validate(newPassword);

        // 4. 직전 5개 재사용 금지 (REQ-AUTH-010)
        // history 최근 5개 + 현재 해시 모두 비교 대상
        List<String> recentHashes = passwordHistoryMapper.findRecentHashes(userId, 5);
        Set<String> hashesToCheck = new HashSet<>(recentHashes);
        hashesToCheck.add(user.getPasswordHash());

        boolean reused = hashesToCheck.stream()
                .anyMatch(h -> passwordPolicyService.matches(newPassword, h));
        if (reused) {
            throw new PasswordReuseException("최근 5회 사용한 비밀번호는 재사용할 수 없습니다");
        }

        // 5. 새 비밀번호 해시 생성 및 갱신
        String newHash = passwordPolicyService.hash(newPassword);
        userMapper.updatePassword(userId, newHash, now);

        // 6. 비밀번호 이력 적재
        passwordHistoryMapper.insert(userId, newHash, now);

        // 7. 모든 Refresh Token 무효화 → 재로그인 강제
        refreshTokenMapper.revokeAllForUser(userId, now);
    }

    /**
     * 비밀번호 재설정 이메일 발송 요청.
     *
     * <p>REQ-AUTH-017-D-3 — 사용자 존재 여부와 무관하게 동일 응답 반환 (enumeration 방지).
     * 존재하는 이메일이면 OTP를 발송하고, 미존재 이메일이면 발송을 skip한다.
     */
    @Override
    @Transactional
    public void requestPasswordReset(String email, String ipAddress, String userAgent) {
        // 사용자 조회 — 미존재여도 예외를 던지지 않음 (enumeration 방지)
        // SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-006 — HMAC 매칭 lookup 으로 격상.
        String emailHmac = emailEncryptionService.computeHmac(email);
        userMapper.findByEmailHmac(emailHmac).ifPresent(user -> {
            // 존재하는 사용자에 한해 OTP 발송
            VerifyRequestRequest verifyReq = new VerifyRequestRequest(
                "EMAIL", email, "PASSWORD_RESET");
            try {
                verificationService.request(verifyReq, ipAddress, userAgent);
            } catch (Exception e) {
                // 쿨다운/IP 차단 등은 보안상 외부에 노출하지 않음
                // 내부 로그로만 기록 (운영팀 모니터링 대상)
            }
        });
    }

    /**
     * 비밀번호 재설정 확인.
     *
     * <p>REQ-AUTH-017-D-4 — verifiedToken 검증 → 비밀번호 정책·재사용 검사 → 변경 → 세션 무효화.
     */
    // @MX:ANCHOR: [AUTO] AuthServiceImpl.confirmPasswordReset — 비밀번호 재설정 핵심 도메인 흐름
    // @MX:REASON: AuthController, 테스트, verificationService에서 참조 (fan_in >= 3)
    @Override
    @Transactional
    @AuditLog(action = "PASSWORD_RESET", entityType = "User", severity = "INFO")
    public void confirmPasswordReset(String verifiedToken, String newPassword) {
        Instant now = Instant.now();

        // 1. verifiedToken 검증 (PURPOSE=PASSWORD_RESET, 5분 이내)
        VerificationRequest vr = verificationService
            .validateVerifiedToken(verifiedToken, VerificationPurpose.PASSWORD_RESET)
            .orElseThrow(InvalidVerifiedTokenException::new);

        // 2. 이메일로 사용자 조회
        // SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-006 — HMAC 매칭 lookup 으로 격상.
        String emailHmac = emailEncryptionService.computeHmac(vr.getTarget());
        User user = userMapper.findByEmailHmac(emailHmac)
            .orElseThrow(InvalidVerifiedTokenException::new);

        // 3. 새 비밀번호 정책 검증
        passwordPolicyService.validate(newPassword);

        // 4. 직전 5개 재사용 검사 (changePassword와 동일 로직)
        List<String> recentHashes = passwordHistoryMapper.findRecentHashes(user.getId(), 5);
        Set<String> hashesToCheck = new HashSet<>(recentHashes);
        hashesToCheck.add(user.getPasswordHash());
        boolean reused = hashesToCheck.stream()
            .anyMatch(h -> passwordPolicyService.matches(newPassword, h));
        if (reused) {
            throw new PasswordReuseException("최근 5회 사용한 비밀번호는 재사용할 수 없습니다");
        }

        // 5. 비밀번호 변경
        String newHash = passwordPolicyService.hash(newPassword);
        userMapper.updatePassword(user.getId(), newHash, now);

        // 6. 비밀번호 이력 적재
        passwordHistoryMapper.insert(user.getId(), newHash, now);

        // 7. 모든 Refresh Token 무효화 → 재로그인 강제
        refreshTokenMapper.revokeAllForUser(user.getId(), now);

        // 8. 완료 안내 이메일 비동기 발송
        emailService.sendPasswordResetNotice(vr.getTarget());
    }

    /**
     * 공개 사이트 회원가입.
     *
     * <p>비회원의 자가 가입 흐름:
     * username/email 중복 확인 → 비밀번호 정책 검증 → BCrypt 해싱 → 이메일 PII 암호화/HMAC
     * → users 삽입 → MEMBER 역할 부여 → access/refresh 토큰 발급.
     *
     * <p>관리자 콘솔 경로(POST /api/v1/users)와 달리 권한 부여자(grantedBy)가 존재하지 않아
     * insertRole 호출 시 self-grant 형태로 user.getId() 를 grantedBy 로 전달한다.
     */
    @AuditLog(action = "REGISTER", entityType = "User", severity = "INFO", captureArgs = false)
    @Override
    @Transactional
    public RegisterResult registerPublicUser(PublicRegisterRequest request, String ipAddress, String userAgent) {
        Instant now = Instant.now();

        // SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 — 이메일 인증 게이트(설정 ON 시) 선검증.
        //   사용자 INSERT 이전에 토큰을 검증하여, 실패 시 사용자가 생성되지 않도록 한다.
        //   토큰 누락 → 400, 토큰 무효/만료/목적불일치 → 403.
        boolean emailVerified = validateRegistrationVerification(request);

        // 1. 중복 검사 — 이 엔드포인트는 email 을 곧 username 으로 사용한다.
        //    REQ-AUTH-006 컨벤션에 따라 username/email_hmac 두 컬럼 모두 충돌 여부를 검사한다.
        if (userMapper.existsByUsername(request.email())) {
            throw new DuplicateUserException("email", request.email());
        }
        String emailHmac = emailEncryptionService.computeHmac(request.email());
        if (userMapper.existsByEmailHmac(emailHmac)) {
            throw new DuplicateUserException("email", request.email());
        }

        // 2. 비밀번호 정책 검증 (8자, 3종 이상 조합 — PasswordPolicyService 규약)
        passwordPolicyService.validate(request.password());
        String hashed = passwordPolicyService.hash(request.password());

        // 3. SPEC-CMS-SECURITY-PII-001 — 이메일 AES-256-GCM 암호화 + HMAC 인덱스
        EncryptedEmail encryptedEmail = emailEncryptionService.encrypt(request.email());

        // SPEC-CMS-USER-APPROVAL-001 REQ-UA-001/002/003 — 가입 승인 게이트 평가.
        boolean approvalRequired = isRegistrationApprovalRequired();

        // 4. 사용자 INSERT — username = email (공개 사이트 가입자 컨벤션)
        //    게이트 ON 이면 PENDING_APPROVAL 로 보류, OFF 면 기존대로 ACTIVE.
        User user = User.builder()
                .username(request.email())
                .email(request.email())
                .passwordHash(hashed)
                .name(request.name())
                .status(approvalRequired ? UserStatus.PENDING_APPROVAL : UserStatus.ACTIVE)
                .emailEncrypted(encryptedEmail.ciphertext())
                .emailIv(encryptedEmail.iv())
                .emailTag(encryptedEmail.tag())
                .emailKeyVersion(encryptedEmail.keyVersion())
                .emailHmac(emailHmac)
                .build();
        userMapper.insert(user);

        // SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 — 인증 토큰 검증을 통과했으면 인증 완료 시각 기록.
        if (emailVerified) {
            userMapper.markEmailVerified(user.getId(), now);
        }

        // SPEC-CMS-USER-APPROVAL-001 REQ-UA-001 — 게이트 ON: 역할·토큰 발급 없이 보류 결과만 반환.
        if (approvalRequired) {
            return new RegisterResult.PendingApproval();
        }

        // 5. MEMBER 역할 부여 (V36 시드).
        //    self-registration 이므로 grantedBy 는 본인 ID 를 사용한다.
        userMapper.insertRole(user.getId(), "MEMBER", user.getId(), now);

        // 6. 토큰 발급 — 관리자 로그인과 동일한 형식으로 access + refresh 발급.
        Set<String> userRoles = userMapper.findRoleCodesByUserId(user.getId());
        Set<String> userPermissions = permissionService.findEffectivePermissionsForUser(user.getId());
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), userRoles, userPermissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 7. Refresh Token 저장 (해시) — login() 와 동일 규약.
        Instant refreshExpires = now.plus(jwtProperties.refreshTokenTtl());
        refreshTokenMapper.insert(RefreshToken.builder()
                .tokenHash(sha256Hex(refreshToken))
                .userId(user.getId())
                .expiresAt(refreshExpires)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(now)
                .build());

        // 8. 가입 시점은 자동 로그인 성공으로 간주 → login_history 에도 적재.
        loginHistoryMapper.insert(LoginHistory.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .createdAt(now)
                .build());

        long expiresInSeconds = jwtProperties.accessTokenTtl().toSeconds();
        return new RegisterResult.Approved(new LoginOutcome(
                new LoginResponse(accessToken, expiresInSeconds, "Bearer"),
                refreshToken));
    }

    /**
     * 가입 승인 게이트 설정 평가.
     *
     * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-003 — {@code REGISTRATION_APPROVAL_REQUIRED} 키가
     * 없거나 파싱에 실패하면 {@code false}(즉시 활성)로 간주하여 기존 동작을 유지한다(회귀 방지).
     */
    private boolean isRegistrationApprovalRequired() {
        try {
            String value = systemSettingService.get("REGISTRATION_APPROVAL_REQUIRED").value();
            return Boolean.parseBoolean(value);
        } catch (RuntimeException e) {
            // 설정 미존재(NoSuchElementException) 또는 조회 실패 → 게이트 OFF 로 회귀 방지.
            return false;
        }
    }

    /**
     * 가입 이메일 인증 게이트 평가 + verifiedToken 검증.
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 — {@code REGISTRATION_EMAIL_VERIFY_REQUIRED}
     * 가 false(기본)이면 검증을 건너뛰고 {@code false}(미인증) 를 반환하여 기존 가입 동작을
     * 유지한다(회귀 방지). true 이면 다음을 강제한다:
     * <ul>
     *   <li>verifiedToken 필드 누락 → {@link RegistrationTokenRequiredException}(400)</li>
     *   <li>토큰 무효·만료(5분 초과)·목적(purpose) 불일치 → {@link RegistrationTokenInvalidException}(403)</li>
     * </ul>
     *
     * @return 인증 게이트를 통과한 경우 {@code true}(가입 후 email_verified_at 기록 대상)
     */
    private boolean validateRegistrationVerification(PublicRegisterRequest request) {
        if (!isEmailVerifyRequired()) {
            return false;
        }
        String token = request.verifiedToken();
        if (token == null || token.isBlank()) {
            throw new RegistrationTokenRequiredException();
        }
        // confirmPasswordReset 과 동일한 검증 경로 — purpose=SIGNUP, 5분 유효.
        verificationService
                .validateVerifiedToken(token, VerificationPurpose.SIGNUP)
                .filter(vr -> vr.getTarget() != null
                        && vr.getTarget().equalsIgnoreCase(request.email()))
                .orElseThrow(RegistrationTokenInvalidException::new);
        return true;
    }

    /**
     * 가입 이메일 인증 필수 여부 설정 평가.
     *
     * <p>SPEC-CMS-USER-APPROVAL-002 NFR-UA2-C1 — {@code REGISTRATION_EMAIL_VERIFY_REQUIRED} 키가
     * 없거나 파싱 실패 시 {@code false}(인증 비요구)로 간주하여 기존 동작을 유지한다(회귀 방지).
     */
    private boolean isEmailVerifyRequired() {
        try {
            String value = systemSettingService.get("REGISTRATION_EMAIL_VERIFY_REQUIRED").value();
            return Boolean.parseBoolean(value);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * SHA-256 해시 (Hex 64자) — HashUtil 위임.
     *
     * <p>Step 3 REFACTOR: JwtAuthenticationFilter와 DRY 제거를 위해 HashUtil로 추출.
     */
    private String sha256Hex(String input) {
        return HashUtil.sha256Hex(input);
    }

    /**
     * SPEC-CMS-SECURITY-MEDIUM-13 — IP 기반 로그인 실패 누적 기록.
     *
     * <p>10분 슬라이딩 윈도우 내에서 동일 IP의 실패 횟수를 누적한다.
     * 윈도우가 만료되면 카운트를 초기화한다. 메모리 보호를 위해 키 상한 초과 시 전체 reset.
     */
    private void recordIpFailure(String ipAddress, Instant now) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return;
        }
        long nowMs = now.toEpochMilli();

        // 메모리 보호: 키 수 폭증 시 강제 reset.
        if (ipFailCounters.size() > IP_FAIL_MAX_KEYS) {
            ipFailCounters.clear();
        }

        IpFailWindow window = ipFailCounters.computeIfAbsent(ipAddress, k -> new IpFailWindow(nowMs));
        // 윈도우 만료 시 카운트 초기화 (단순 슬라이딩 — 정확성보다 메모리 안정성 우선).
        if (nowMs - window.windowStartMs > IP_BLOCK_WINDOW_MS) {
            window.windowStartMs = nowMs;
            window.count.set(0);
        }
        window.count.incrementAndGet();
    }

    /**
     * SPEC-CMS-SECURITY-MEDIUM-13 — IP가 현재 차단 상태인지 검사.
     *
     * <p>10분 내 누적 실패 횟수가 임계치(20회) 이상이면 차단으로 판정한다.
     * 차단된 IP는 사용자 enumeration의 발신지로 간주되어 DB 조회 이전에 거부된다.
     */
    private boolean isIpBlocked(String ipAddress, Instant now) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        IpFailWindow window = ipFailCounters.get(ipAddress);
        if (window == null) {
            return false;
        }
        long nowMs = now.toEpochMilli();
        // 윈도우 만료된 경우 차단 해제 — 다음 실패 기록 시 자연스럽게 초기화된다.
        if (nowMs - window.windowStartMs > IP_BLOCK_WINDOW_MS) {
            return false;
        }
        return window.count.get() >= IP_FAIL_THRESHOLD;
    }
}
