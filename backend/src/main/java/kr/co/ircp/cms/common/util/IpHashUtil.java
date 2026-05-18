package kr.co.ircp.cms.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 클라이언트 IP를 SHA-256 해시(64자 hex)로 변환하는 공용 유틸.
 *
 * <p>보안 불변식: 평문 IP는 이 메서드의 인자로만 존재하며 즉시 해시되어
 * 어떤 필드/변수/로그에도 평문으로 보관되지 않는다.
 * SearchController·SimulationController 등 IP 해시가 필요한 모든 곳에서 공유한다.
 */
// @MX:ANCHOR: [AUTO] IpHashUtil.sha256Hex — IP 비식별화 단일 진입점
// @MX:REASON: SimulationController/SearchController 등 다중 호출, 평문 IP 미저장 불변식의 핵심 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-AI-001
public final class IpHashUtil {

    private IpHashUtil() {
    }

    /**
     * 평문 IP를 SHA-256 hex(소문자 64자)로 해시한다.
     *
     * @param remoteAddr 평문 IP (null/blank 허용 — 그 경우 null 반환)
     * @return SHA-256 hex 문자열 또는 null
     */
    public static String sha256Hex(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return null;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(remoteAddr.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK 표준 — 사실상 도달 불가
            return null;
        }
    }
}
