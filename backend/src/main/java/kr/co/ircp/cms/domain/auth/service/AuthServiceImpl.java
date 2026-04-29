package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.entity.LoginHistory;
import kr.co.ircp.cms.domain.auth.entity.RefreshToken;
import kr.co.ircp.cms.domain.auth.entity.TokenBlacklist;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.exception.TokenReuseException;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

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
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            LoginHistoryMapper loginHistoryMapper,
            TokenBlacklistMapper tokenBlacklistMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordPolicyService passwordPolicyService,
            JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.loginHistoryMapper = loginHistoryMapper;
        this.tokenBlacklistMapper = tokenBlacklistMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordPolicyService = passwordPolicyService;
        this.jwtProperties = jwtProperties;
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

        // 1. 사용자 조회 — 미존재 시 실패 이력 기록 후 예외
        User user = userMapper.findByUsername(req.username()).orElse(null);
        if (user == null) {
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

        // 4. 비밀번호 검증
        if (!passwordPolicyService.matches(req.password(), user.getPasswordHash())) {
            // 실패 횟수 증가
            userMapper.incrementFailCount(req.username(), now);
            int newFailCount = user.getFailCount() + 1;

            // 5회 이상 실패 시 계정 잠금 (REQ-AUTH-005)
            if (newFailCount >= 5) {
                userMapper.lockAccount(req.username(), now.plusSeconds(30 * 60));
            }

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

        // 6. 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), req.username(), Set.of());
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

        // 5. username 결정 — UserMapper에 findById 없음; userId를 string fallback으로 사용
        // Role 조회도 이번 단계에서는 빈 Set — 다음 사이클에서 UserRoleMapper 연동
        String username = String.valueOf(userId);

        // 6. Rotation — 기존 무효화
        refreshTokenMapper.revoke(tokenHash, now);

        // 7. 새 토큰 쌍 발급
        String newAccess = jwtTokenProvider.generateAccessToken(userId, username, Set.of());
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
     * SHA-256 해시 (Hex 64자) — HashUtil 위임.
     *
     * <p>Step 3 REFACTOR: JwtAuthenticationFilter와 DRY 제거를 위해 HashUtil로 추출.
     */
    private String sha256Hex(String input) {
        return HashUtil.sha256Hex(input);
    }
}
