package kr.co.ircp.cms.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JWT 토큰 생성·검증 서비스 구현체 (Step 2 GREEN).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001/002/003 — jjwt 0.12.6 기반 HS256 서명.
 *
 * // @MX:NOTE: [AUTO] jjwt 0.12.6 API — Jwts.parser().verifyWith() 사용 (구버전 setSigningKey 아님)
 * // @MX:REASON: jjwt 0.11.x 이하의 Jwts.parser().setSigningKey()는 0.12.x에서 제거됨
 */
@Service
public class JwtTokenProviderImpl implements JwtTokenProvider {

    // @MX:WARN: [AUTO] 시크릿 키 최소 길이 미충족 시 IllegalStateException — 운영 환경 필수 재정의
    // @MX:REASON: HS256 최소 256비트(32바이트) 미충족 시 jjwt가 WeakKeyException 발생
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    /**
     * 기본 생성자 — 단위 테스트 전용 기본 JwtProperties 사용.
     *
     * <p>SPEC-CMS-SECURITY-JWT — 운영 환경에서는 스프링 DI로 환경변수 기반 시크릿이 주입된다.
     * 본 생성자는 단위 테스트 헬퍼 용도이며, 운영 빈 생성에는 사용되지 않는다.
     *
     * @deprecated 운영 코드에서 직접 호출 금지. 스프링 DI를 통한 {@link #JwtTokenProviderImpl(JwtProperties)} 사용 권장.
     */
    @Deprecated
    protected JwtTokenProviderImpl() {
        this(new JwtProperties(
                "test-only-jwt-secret-256-bits-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "iroum-cms"
        ));
    }

    /**
     * JwtProperties 주입 생성자 — Spring 컨텍스트 DI 및 테스트 목 주입용.
     *
     * @param jwtProperties JWT 설정 프로퍼티
     */
    public JwtTokenProviderImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = buildSigningKey(jwtProperties.secret());
    }

    /**
     * 부팅 시 시크릿 보안 검증.
     *
     * <p>SPEC-CMS-SECURITY-JWT — 운영 부팅 시 'changeme' 접두사 시크릿 차단.
     * application-local.yml 등에서 의도적으로 dev-only 시크릿을 사용할 때를 위해
     * "local-dev-only" 접두사는 허용.
     */
    @PostConstruct
    void validateSecret() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.startsWith("changeme")) {
            throw new IllegalStateException(
                    "JWT secret이 안전하지 않은 기본값입니다. JWT_SECRET 환경변수로 256비트 이상 보안 키를 주입하세요.");
        }
    }

    // @MX:ANCHOR: [AUTO] generateAccessToken — Access Token 생성 계약 (fan_in >= 3: AuthService, 필터, 테스트)
    // @MX:REASON: 인증·재발급·테스트 등 다수 호출 지점 — 클레임 구조 변경 시 전 흐름 영향
    @Override
    public String generateAccessToken(long userId, String username, Set<String> roles, Set<String> permissions) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.accessTokenTtl());

        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("roles", roles)
                .claim("permissions", permissions != null ? permissions : Set.of())
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public String generateRefreshToken(long userId) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.refreshTokenTtl());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Optional<JwtClaims> validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            long uid = claims.get("uid", Long.class);
            String username = claims.getSubject();

            // jjwt-jackson이 roles를 List<String>으로 역직렬화
            @SuppressWarnings("unchecked")
            List<String> roleList = claims.get("roles", List.class);
            Set<String> roles = (roleList == null) ? Set.of() : new HashSet<>(roleList);

            // permissions 클레임 추출 (REQ-AUTH-013)
            @SuppressWarnings("unchecked")
            List<String> permList = claims.get("permissions", List.class);
            Set<String> permissions = (permList == null) ? Set.of() : new HashSet<>(permList);

            Instant expiresAt = claims.getExpiration().toInstant();

            return Optional.of(new JwtClaims(uid, username, roles, permissions, expiresAt));

        } catch (ExpiredJwtException e) {
            // 만료된 토큰 — TokenExpiredException으로 변환
            throw new TokenExpiredException("Access Token이 만료되었습니다");
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 형식 오류 등 — empty 반환
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> extractUserId(String refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();

            String subject = claims.getSubject();
            return Optional.of(Long.parseLong(subject));

        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 시크릿 문자열로 {@link SecretKey}를 생성한다.
     *
     * <p>HS256 최소 요건: 256비트(32바이트). 미충족 시 IllegalStateException 발생.
     *
     * @param secret 시크릿 문자열 (UTF-8)
     * @return SecretKey
     */
    private SecretKey buildSigningKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret은 최소 256비트(32바이트)이어야 합니다. 현재: " + keyBytes.length + "바이트");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
