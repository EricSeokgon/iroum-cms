package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;

/**
 * 비밀번호 정책 서비스 인터페이스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004 — 8자 이상, 영문/숫자/특수문자 3종류 이상,
 * BCrypt strength=12 해싱 정책을 적용한다.
 */
public interface PasswordPolicyService {

    /**
     * 비밀번호 정책 검증.
     *
     * <p>아래 조건을 모두 충족해야 한다:
     * <ul>
     *   <li>8자 이상</li>
     *   <li>영문 대문자, 소문자, 숫자, 특수문자 중 3종류 이상</li>
     * </ul>
     *
     * @param rawPassword 검증할 평문 비밀번호
     * @throws PasswordPolicyViolationException 정책 위반 시
     */
    void validate(String rawPassword) throws PasswordPolicyViolationException;

    /**
     * 평문 비밀번호를 BCrypt(strength=12)로 해싱.
     *
     * @param rawPassword 평문 비밀번호
     * @return BCrypt 해시 문자열
     */
    String hash(String rawPassword);

    /**
     * 평문 비밀번호와 BCrypt 해시 비교.
     *
     * @param rawPassword 평문 비밀번호
     * @param hash BCrypt 해시
     * @return 일치하면 true
     */
    boolean matches(String rawPassword, String hash);
}
