package kr.co.ircp.cms.domain.security.pii.kms;

import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HashiCorp Vault Transit 기반 PiiKeyVault 구현체.
 *
 * <p>설정 (application.yml or env):
 * <pre>
 * pii:
 *   keyvault:
 *     provider: vault-transit
 *     vault-transit:
 *       key-name: pii-dek
 *       active-version: 1
 *       endpoint-url: http://localhost:8200
 *       token: hvs.XXXXXXXXXXXX
 *       encrypted-keys:
 *         dek-v1: vault:v1:BASE64_CIPHERTEXT_DEK_V1
 *         hmac:   vault:v1:BASE64_CIPHERTEXT_HMAC
 * </pre>
 *
 * <p>동작 방식:
 * 1. 부팅 시 {@link VaultTemplate#opsForTransit()}.decrypt() 호출 → 모든 암호화된 키 복호화 → 메모리 캐시
 * 2. 캐시된 키로 PiiKeyVault 인터페이스 메서드 처리
 * 3. {@link ConcurrentHashMap} 가 캐시 역할을 한다 (AWS KMS 구현과 동일 패턴, Caffeine 미사용)
 *
 * <p>키 매핑 규칙:
 * - "dek-v1" → version 1 DEK (AES)
 * - "dek-v2" → version 2 DEK (AES)
 * - "hmac"   → HMAC-SHA256 키
 *
 * <p>Fail-fast 정책: Vault 서버 미가용 / 복호화 실패 / 활성 버전 누락 시 부팅 시점에 예외 발생.
 *
 * <p>Vault Transit API 노트:
 * - {@code VaultTransitOperations.decrypt(String keyName, Ciphertext ciphertext)} 는 {@link Plaintext} 반환
 * - {@code Plaintext.getPlaintext()} 는 이미 base64 디코딩된 raw byte[] 를 반환 (Spring Vault 내부 처리)
 * - 따라서 추가 base64 디코딩 불필요
 *
 * @MX:ANCHOR: [AUTO] VaultTransitPiiKeyVault — PiiKeyVault 구현체 (fan_in >= 3 예상)
 * @MX:REASON: Vault Transit 기반 DEK 관리 단일 진입점
 * @MX:SPEC: SPEC-CMS-SECURITY-PII-KMS-001
 */
@Component
@ConditionalOnProperty(name = "pii.keyvault.provider", havingValue = "vault-transit")
@EnableConfigurationProperties(VaultTransitPiiKeyVaultProperties.class)
public class VaultTransitPiiKeyVault implements PiiKeyVault {

    private static final String AES_ALGORITHM = "AES";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String DEK_PREFIX = "dek-v";
    private static final String HMAC_KEY_NAME = "hmac";

    private final int activeVersion;
    private final Map<Integer, SecretKey> dataKeys;
    private final SecretKey hmacKey;

    /**
     * 운영용 + 테스트용 단일 생성자.
     *
     * <p>운영 환경에서는 Spring Cloud Vault 자동설정이 제공하는 {@link VaultTemplate} 빈이 주입된다.
     * 단위 테스트는 Mockito 로 {@code VaultTemplate} 을 모킹하여 동일 생성자를 직접 호출한다.
     *
     * <p>(AWS KMS 와 달리 Vault 는 클라이언트 빌더가 단순하지 않으므로, KMS 의 위임 패턴 대신
     * 단일 생성자로 통합한다. Spring 의 자동 와이어링 + 테스트 모킹 모두 본 시그니처로 충분하다.)
     */
    @Autowired
    public VaultTransitPiiKeyVault(
            VaultTransitPiiKeyVaultProperties properties,
            VaultTemplate vaultTemplate
    ) {
        this.activeVersion = properties.activeVersion();
        this.dataKeys = new ConcurrentHashMap<>();

        // 키 이름 검증 — Transit named key 가 없으면 부팅 즉시 실패한다.
        String keyName = properties.keyName();
        if (keyName == null || keyName.isBlank()) {
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: Transit key 이름(pii.keyvault.vault-transit.key-name)이 비어있습니다"
            );
        }

        Map<String, String> encryptedKeys = properties.encryptedKeys();
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: 암호화된 키 맵(pii.keyvault.vault-transit.encrypted-keys)이 비어있습니다"
            );
        }

        SecretKey resolvedHmacKey = null;

        // Vault Transit 복호화는 부팅 시점 1회만 수행 → 모든 키를 디코딩하여 in-memory cache 에 적재.
        try {
            VaultTransitOperations transitOps = vaultTemplate.opsForTransit();

            for (Map.Entry<String, String> entry : encryptedKeys.entrySet()) {
                String name = entry.getKey();
                String ciphertext = entry.getValue();

                if (ciphertext == null || ciphertext.isBlank()) {
                    throw new PiiKeyVaultException(
                            "VaultTransitPiiKeyVault: 키 '" + name + "' 의 ciphertext 가 비어있습니다"
                    );
                }

                byte[] plaintext = decryptViaVault(transitOps, keyName, name, ciphertext);

                if (HMAC_KEY_NAME.equals(name)) {
                    if (plaintext.length < 32) {
                        throw new PiiKeyVaultException(
                                "VaultTransitPiiKeyVault: HMAC 키 길이 부족 (필요: 32+ bytes, 실제: "
                                        + plaintext.length + ")"
                        );
                    }
                    resolvedHmacKey = new SecretKeySpec(plaintext, HMAC_ALGORITHM);
                } else if (name.startsWith(DEK_PREFIX)) {
                    int version = parseDekVersion(name);
                    if (plaintext.length != 32) {
                        throw new PiiKeyVaultException(
                                "VaultTransitPiiKeyVault: DEK v" + version + " 길이가 올바르지 않습니다 "
                                        + "(필요: 32 bytes / 256 bits, 실제: " + plaintext.length + " bytes)"
                        );
                    }
                    this.dataKeys.put(version, new SecretKeySpec(plaintext, AES_ALGORITHM));
                } else {
                    throw new PiiKeyVaultException(
                            "VaultTransitPiiKeyVault: 알 수 없는 키 이름 '" + name
                                    + "' (허용: 'dek-v{N}' 또는 'hmac')"
                    );
                }
            }
        } catch (VaultException e) {
            // Vault API 호출 실패 — 부팅 차단 (D4: fail-fast).
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: Vault Transit 키 복호화 실패 — " + e.getMessage(), e
            );
        } catch (PiiKeyVaultException e) {
            // 이미 변환된 도메인 예외는 그대로 위로 던진다 (메시지 손실 방지).
            throw e;
        } catch (RuntimeException e) {
            // 그 외 런타임 예외 (네트워크, 인증, JSON 파싱 등) — fail-fast 로 부팅 차단.
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: Vault Transit 호출 중 예기치 못한 오류 — " + e.getMessage(), e
            );
        }

        if (this.dataKeys.isEmpty()) {
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: DEK(dek-v*) 가 하나도 설정되지 않았습니다"
            );
        }
        if (!this.dataKeys.containsKey(activeVersion)) {
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: 활성 키 버전 " + activeVersion + " 에 해당하는 DEK 가 없습니다"
            );
        }
        if (resolvedHmacKey == null) {
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: HMAC 키('hmac')가 encrypted-keys 에 설정되지 않았습니다"
            );
        }

        this.hmacKey = resolvedHmacKey;
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
                    "VaultTransitPiiKeyVault: 키 버전 " + version + " 이 존재하지 않습니다"
            );
        }
        return key;
    }

    @Override
    public SecretKey getHmacKey() {
        return hmacKey;
    }

    /**
     * Vault Transit decrypt 호출 후 평문 바이트 반환.
     *
     * <p>Spring Vault 의 {@link VaultTransitOperations#decrypt(String, Ciphertext)} 는
     * {@link Plaintext} 를 반환하며, {@code getPlaintext()} 는 이미 base64 디코딩된 raw byte[] 다.
     *
     * @param transitOps Vault Transit Operations
     * @param keyName    Vault Transit named key (예: "pii-dek")
     * @param entryName  키 엔트리 이름 (오류 메시지용, 예: "dek-v1")
     * @param ciphertext Vault Transit ciphertext 문자열 (예: "vault:v1:...")
     * @return 복호화된 평문 바이트 (raw AES/HMAC key material)
     */
    private byte[] decryptViaVault(
            VaultTransitOperations transitOps,
            String keyName,
            String entryName,
            String ciphertext
    ) {
        try {
            Plaintext plaintext = transitOps.decrypt(keyName, Ciphertext.of(ciphertext));
            byte[] bytes = plaintext.getPlaintext();
            if (bytes == null || bytes.length == 0) {
                throw new PiiKeyVaultException(
                        "VaultTransitPiiKeyVault: 키 '" + entryName + "' 복호화 결과가 비어있습니다"
                );
            }
            return bytes;
        } catch (VaultException e) {
            // 호출자가 한 번 더 메시지를 감싸지만, 어떤 키에서 실패했는지 추적할 수 있게 entryName 포함.
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: 키 '" + entryName + "' Vault Transit decrypt 실패", e
            );
        }
    }

    /**
     * "dek-v1", "dek-v2" 형식에서 버전 번호 추출.
     */
    private int parseDekVersion(String name) {
        String suffix = name.substring(DEK_PREFIX.length());
        try {
            int version = Integer.parseInt(suffix);
            if (version < 1) {
                throw new PiiKeyVaultException(
                        "VaultTransitPiiKeyVault: DEK 버전은 1 이상이어야 합니다 (입력: " + name + ")"
                );
            }
            return version;
        } catch (NumberFormatException e) {
            throw new PiiKeyVaultException(
                    "VaultTransitPiiKeyVault: DEK 키 이름 '" + name + "' 의 버전 번호 파싱 실패", e
            );
        }
    }
}
