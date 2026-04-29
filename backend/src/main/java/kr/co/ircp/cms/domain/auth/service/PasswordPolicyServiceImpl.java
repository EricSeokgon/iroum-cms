package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 정책 서비스 구현체 (Step 2 GREEN).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004 — 8자 이상, 3종류 이상 문자 조합 검증 및 BCrypt strength=12 해싱.
 *
 * // @MX:NOTE: [AUTO] BCryptPasswordEncoder(12) — 독립 인스턴스 사용
 * // @MX:REASON: SecurityConfig의 PasswordEncoder 빈을 주입받을 경우 테스트에서 new PasswordPolicyServiceImpl()
 * //             기본 생성자 사용이 불가하여, 자체 BCryptPasswordEncoder(12) 인스턴스를 생성한다.
 */
@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    // @MX:NOTE: [AUTO] 정책 상수 — REQ-AUTH-004 기준값
    private static final int MIN_LENGTH = 8;
    private static final int MIN_TYPES = 3;

    private final BCryptPasswordEncoder encoder;

    /**
     * 기본 생성자 — BCryptPasswordEncoder(strength=12) 자체 생성.
     *
     * <p>Spring 컨텍스트 외부(단위 테스트)에서 {@code new PasswordPolicyServiceImpl()}로 사용 가능.
     */
    public PasswordPolicyServiceImpl() {
        this.encoder = new BCryptPasswordEncoder(12);
    }

    /**
     * BCryptPasswordEncoder 주입 생성자 — 스프링 빈 DI 또는 테스트 목 주입용.
     *
     * @param encoder BCryptPasswordEncoder 인스턴스
     */
    public PasswordPolicyServiceImpl(BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
    }

    // @MX:ANCHOR: [AUTO] validate — 비밀번호 정책 진입점 (fan_in >= 3: AuthService, hash 호출, 테스트)
    // @MX:REASON: 비밀번호 정책 검증은 인증·변경·가입 모든 흐름에서 호출되므로 계약 불변 유지 필수
    @Override
    public void validate(String rawPassword) throws PasswordPolicyViolationException {
        // null 또는 빈 문자열 거부
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new PasswordPolicyViolationException(
                    "비밀번호는 8자 이상, 3종 이상 조합이어야 합니다");
        }

        // 길이 검사
        if (rawPassword.length() < MIN_LENGTH) {
            throw new PasswordPolicyViolationException(
                    "비밀번호는 8자 이상, 3종 이상 조합이어야 합니다");
        }

        // 문자 종류 카운트
        int types = countCharTypes(rawPassword);
        if (types < MIN_TYPES) {
            throw new PasswordPolicyViolationException(
                    "비밀번호는 8자 이상, 3종 이상 조합이어야 합니다");
        }
    }

    @Override
    public String hash(String rawPassword) {
        // validate 후 해싱 — 정책 위반 비밀번호 해싱 방지
        validate(rawPassword);
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }

    /**
     * 비밀번호에 포함된 문자 종류 수를 반환한다.
     *
     * <p>종류: 대문자 [A-Z], 소문자 [a-z], 숫자 [0-9], 특수문자.
     *
     * @param password 검사할 비밀번호
     * @return 포함된 문자 종류 수 (0 ~ 4)
     */
    private int countCharTypes(String password) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                // 공백을 포함한 나머지 모든 문자를 특수문자로 처리
                hasSpecial = true;
            }
        }

        int count = 0;
        if (hasUpper) count++;
        if (hasLower) count++;
        if (hasDigit) count++;
        if (hasSpecial) count++;
        return count;
    }
}
