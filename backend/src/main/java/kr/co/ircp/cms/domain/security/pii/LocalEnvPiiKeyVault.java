package kr.co.ircp.cms.domain.security.pii;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 환경변수 기반 PiiKeyVault 구현체.
 *
 * <p>설정 (application.yml or env):
 * <pre>
 * pii:
 *   keyvault:
 *     active-version: 1
 *     keys:
 *       v1: BASE64_AES_256_KEY  (32 bytes)
 *     hmac-key: BASE64_HMAC_KEY  (32 bytes)
 * </pre>
 *
 * <p>키 형식:
 * - AES 키: 32 bytes (256 bits) base64 인코딩
 * - HMAC 키: 32 bytes base64 인코딩
 *
 * <p>운영 환경에서는 Spring profile별로 환경변수 주입 (KMS 통합은 후속 SPEC).
 *
 * @MX:WARN [AUTO] 운영 환경에서 키가 평문 환경변수로 주입되므로 secrets manager 통합 권장
 * @MX:REASON KMS/Vault 통합은 SPEC-CMS-SECURITY-PII-002 (후속) 범위
 * @MX:SPEC SPEC-CMS-SECURITY-PII-001#REQ-PII-EMAIL-004
 */
@Component
@ConditionalOnProperty(name = "pii.keyvault.provider", havingValue = "local-env", matchIfMissing = true)
public class LocalEnvPiiKeyVault implements PiiKeyVault {

    private static final String AES_ALGORITHM = "AES";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final int activeVersion;
    private final Map<Integer, SecretKey> dataKeys;
    private final SecretKey hmacKey;

    public LocalEnvPiiKeyVault(
            @Value("${pii.keyvault.active-version:1}") int activeVersion,
            @Value("${pii.keyvault.keys.v1:}") String v1KeyBase64,
            @Value("${pii.keyvault.keys.v2:}") String v2KeyBase64,
            @Value("${pii.keyvault.hmac-key:}") String hmacKeyBase64
    ) {
        this.activeVersion = activeVersion;
        this.dataKeys = new HashMap<>();

        if (!v1KeyBase64.isBlank()) {
            this.dataKeys.put(1, decodeAesKey(v1KeyBase64, 1));
        }
        if (!v2KeyBase64.isBlank()) {
            this.dataKeys.put(2, decodeAesKey(v2KeyBase64, 2));
        }

        if (this.dataKeys.isEmpty()) {
            throw new PiiKeyVaultException(
                    "PII KeyVault: 데이터 암호화 키가 하나도 설정되지 않았습니다 " +
                    "(pii.keyvault.keys.v1 또는 v2 환경변수 필요)"
            );
        }

        if (!this.dataKeys.containsKey(activeVersion)) {
            throw new PiiKeyVaultException(
                    "PII KeyVault: 활성 키 버전 " + activeVersion + "에 해당하는 키가 없습니다"
            );
        }

        if (hmacKeyBase64.isBlank()) {
            throw new PiiKeyVaultException(
                    "PII KeyVault: HMAC 키가 설정되지 않았습니다 (pii.keyvault.hmac-key 환경변수 필요)"
            );
        }
        this.hmacKey = decodeHmacKey(hmacKeyBase64);
    }

    @Override
    public ActiveKey getActiveDataEncryptionKey() {
        return new ActiveKey(activeVersion, getDataEncryptionKey(activeVersion));
    }

    @Override
    public SecretKey getDataEncryptionKey(int version) {
        SecretKey key = dataKeys.get(version);
        if (key == null) {
            throw new PiiKeyVaultException(
                    "PII KeyVault: 키 버전 " + version + "이 존재하지 않습니다"
            );
        }
        return key;
    }

    @Override
    public SecretKey getHmacKey() {
        return hmacKey;
    }

    private SecretKey decodeAesKey(String base64, int version) {
        try {
            byte[] decoded = Base64.getDecoder().decode(Objects.requireNonNull(base64).trim());
            if (decoded.length != 32) {
                throw new PiiKeyVaultException(
                        "PII KeyVault: 키 v" + version + " 길이가 올바르지 않습니다 " +
                        "(필요: 32 bytes / 256 bits, 실제: " + decoded.length + " bytes)"
                );
            }
            return new SecretKeySpec(decoded, AES_ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new PiiKeyVaultException(
                    "PII KeyVault: 키 v" + version + " base64 디코딩 실패", e);
        }
    }

    private SecretKey decodeHmacKey(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64.trim());
            if (decoded.length < 32) {
                throw new PiiKeyVaultException(
                        "PII KeyVault: HMAC 키 길이 부족 (필요: 32+ bytes, 실제: " + decoded.length + ")"
                );
            }
            return new SecretKeySpec(decoded, HMAC_ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new PiiKeyVaultException("PII KeyVault: HMAC 키 base64 디코딩 실패", e);
        }
    }
}
