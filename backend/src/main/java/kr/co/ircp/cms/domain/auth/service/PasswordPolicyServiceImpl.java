package kr.co.ircp.cms.domain.auth.service;

import org.springframework.stereotype.Service;

/**
 * PasswordPolicyService RED 단계 구현체.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004 — BCrypt 해싱 및 정책 검증은 Step 2 GREEN에서 구현.
 * 현재 모든 메서드는 {@link UnsupportedOperationException}을 던져 RED 상태를 유지한다.
 *
 * @MX:TODO: [AUTO] Step 2 GREEN — BCrypt strength=12, 8자/3종 정책 검증 구현
 */
@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    @Override
    public void validate(String rawPassword) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public String hash(String rawPassword) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        // RED — Step 2 GREEN에서 구현
        throw new UnsupportedOperationException("RED — Step 2 GREEN에서 구현");
    }
}
