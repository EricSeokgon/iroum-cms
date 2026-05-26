package kr.co.ircp.cms.domain.security.pii.kms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

// @MX:NOTE: [AUTO] Vault Transit 키 설정 — 암호화된 DEK/HMAC 키를 환경변수 또는 yml에서 주입
// @MX:SPEC: SPEC-CMS-SECURITY-PII-KMS-001
/**
 * HashiCorp Vault Transit 기반 PiiKeyVault 설정.
 *
 * <p>설정 예시 (application.yml):
 * <pre>
 * pii:
 *   keyvault:
 *     provider: vault-transit
 *     vault-transit:
 *       key-name: pii-dek                  # Vault Transit named key
 *       active-version: 1                  # 활성 DEK 버전
 *       endpoint-url: http://localhost:8200
 *       token: hvs.XXXXXXXXXXXX
 *       encrypted-keys:
 *         dek-v1: vault:v1:BASE64_CIPHERTEXT_FOR_DEK_V1
 *         hmac:   vault:v1:BASE64_CIPHERTEXT_FOR_HMAC
 * </pre>
 *
 * <p>{@code encryptedKeys} 값은 Vault Transit 가 반환하는 ciphertext 문자열 형식
 * ({@code vault:v{N}:...})이다. 부팅 시 {@link org.springframework.vault.core.VaultTemplate}
 * 를 통해 모두 복호화된 후 평문 키만 메모리에 캐싱된다.
 */
@ConfigurationProperties(prefix = "pii.keyvault.vault-transit")
public record VaultTransitPiiKeyVaultProperties(
        String keyName,                    // Vault Transit named key (e.g. "pii-dek")
        int activeVersion,                 // 활성 DEK 버전
        String endpointUrl,                // Vault server URL (e.g. http://localhost:8200)
        String token,                      // Vault token (인증용)
        Map<String, String> encryptedKeys  // key: "dek-v1", "dek-v2", "hmac" → value: Vault ciphertext
) {
}
