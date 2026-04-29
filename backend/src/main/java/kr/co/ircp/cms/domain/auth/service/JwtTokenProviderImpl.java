package kr.co.ircp.cms.domain.auth.service;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * JwtTokenProvider RED 단계 구현체.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001/002 — 실제 jjwt 0.12.6 구현은 Step 2 GREEN에서.
 * 현재 모든 메서드는 {@link UnsupportedOperationException}을 던져 RED 상태를 유지한다.
 *
 * @MX:TODO: [AUTO] Step 2 GREEN — jjwt 0.12.6으로 실제 JWT 생성/검증 구현
 */
@Service
public class JwtTokenProviderImpl implements JwtTokenProvider {

    @Override
    public String generateAccessToken(long userId, String username, Set<String> roles) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public String generateRefreshToken(long userId) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public Optional<JwtClaims> validateAccessToken(String token) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public Optional<Long> extractUserId(String refreshToken) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }
}
