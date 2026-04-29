package kr.co.ircp.cms.domain.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 해시 유틸리티 (DRY 추출).
 *
 * <p>AuthServiceImpl, JwtAuthenticationFilter 두 곳에서 동일 로직을 사용하므로
 * 단일 클래스로 추출하여 중복을 제거한다 (SPEC-CMS-002 Step 3 REFACTOR).
 */
// @MX:ANCHOR: [AUTO] HashUtil.sha256Hex — 토큰 해시 계산의 단일 진실점
// @MX:REASON: AuthServiceImpl, JwtAuthenticationFilter, 테스트 등 fan_in >= 3
public final class HashUtil {

    private HashUtil() {
        // 유틸 클래스 — 인스턴스 생성 금지
    }

    /**
     * 문자열을 SHA-256 해시(Hex 64자)로 변환한다.
     *
     * @param input 원본 문자열
     * @return 소문자 16진수 64자 문자열
     * @throws IllegalStateException SHA-256 알고리즘 미지원 환경(실질적으로 발생하지 않음)
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
