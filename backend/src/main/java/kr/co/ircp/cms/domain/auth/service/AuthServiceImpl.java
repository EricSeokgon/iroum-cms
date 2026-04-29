package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AuthService RED 단계 구현체.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003, 011 — 실제 로직은 Step 2 GREEN에서 구현.
 * 현재 모든 메서드는 {@link UnsupportedOperationException}을 던져 RED 상태를 유지한다.
 *
 * @MX:TODO: [AUTO] Step 2 GREEN — 로그인, 토큰 갱신, 로그아웃 전체 흐름 구현
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final LoginHistoryMapper loginHistoryMapper;
    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordPolicyService passwordPolicyService;

    @Override
    public LoginResponse login(LoginRequest req, String ipAddress, String userAgent) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public RefreshResult refresh(String refreshTokenCookie, String ipAddress, String userAgent) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public void logout(String accessToken, String refreshTokenCookie) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }
}
