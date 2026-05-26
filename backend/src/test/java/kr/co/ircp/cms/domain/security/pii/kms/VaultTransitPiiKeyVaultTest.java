package kr.co.ircp.cms.domain.security.pii.kms;

import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;

import javax.crypto.SecretKey;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * VaultTransitPiiKeyVault 단위 테스트 (Mock VaultTemplate).
 *
 * <p>SPEC-CMS-SECURITY-PII-KMS-001 — HashiCorp Vault Transit KEK-DEK 패턴 검증.
 *
 * <p>테스트 전략:
 * - {@link VaultTemplate} 와 {@link VaultTransitOperations} 를 Mockito 로 모킹하여 외부 의존성 제거.
 * - 생성자 시점에 모든 암호화된 키를 복호화하고 in-memory 캐시하는 fail-fast 동작 검증.
 * - {@link Ciphertext} 입력과 {@link Plaintext} 출력을 통한 정상 / 실패 경로 검증.
 *
 * <p>구현 노트(구현 에이전트에게):
 * 본 테스트는 단일 public 생성자를 가정한다:
 * <pre>
 *   VaultTransitPiiKeyVault(VaultTransitPiiKeyVaultProperties props, VaultTemplate vaultTemplate)
 * </pre>
 * 운영용 Spring 빈은 Spring Cloud Vault 가 제공하는 {@code VaultTemplate} 을 자동 주입한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VaultTransitPiiKeyVault 단위 테스트 (Mock VaultTemplate)")
class VaultTransitPiiKeyVaultTest {

    private static final String KEY_NAME = "pii-dek";
    private static final String ENDPOINT_URL = "http://localhost:8200";
    private static final String TOKEN = "test-token";

    // 32-byte 결정적 평문 키들 — 각각 다른 byte 패턴으로 키 분리 검증 가능.
    private static final byte[] DEK_V1_PLAINTEXT = filled((byte) 0x11, 32);
    private static final byte[] HMAC_PLAINTEXT = filled((byte) 0x33, 32);

    // Vault Transit ciphertext 는 항상 "vault:v{N}:..." 형식이다.
    private static final String DEK_V1_CIPHERTEXT = "vault:v1:DEK_V1_CIPHERTEXT_PAYLOAD";
    private static final String HMAC_CIPHERTEXT = "vault:v1:HMAC_CIPHERTEXT_PAYLOAD";

    private static byte[] filled(byte value, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = value;
        }
        return result;
    }

    /**
     * Vault Transit Mock 빌더 — ciphertext 문자열 → 평문 byte[] 매핑으로 decrypt 응답을 구성한다.
     *
     * <p>{@code VaultTemplate.opsForTransit()} 는 {@link VaultTransitOperations} mock 을 반환하며,
     * {@code transitOps.decrypt(keyName, Ciphertext)} 는 매핑된 평문을 {@link Plaintext} 로 감싸 반환한다.
     */
    private static VaultTemplate mockVaultTemplateWith(Map<String, byte[]> ciphertextToPlaintext) {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultTransitOperations transitOps = mock(VaultTransitOperations.class);
        when(vaultTemplate.opsForTransit()).thenReturn(transitOps);

        when(transitOps.decrypt(eq(KEY_NAME), any(Ciphertext.class))).thenAnswer(invocation -> {
            Ciphertext ct = invocation.getArgument(1);
            byte[] plaintext = ciphertextToPlaintext.get(ct.getCiphertext());
            if (plaintext == null) {
                throw new VaultException("Mock: 등록되지 않은 ciphertext 요청 — " + ct.getCiphertext());
            }
            return Plaintext.of(plaintext);
        });
        return vaultTemplate;
    }

    // ──────────────────────────────────────────────
    // Test 1: 정상 초기화 — 모든 키 복호화 + 캐싱
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("initWithValidConfig_loadsAllKeys")
    void initWithValidConfig_loadsAllKeys() {
        // given — Vault Transit 는 dek-v1 과 hmac 의 ciphertext 를 각각 32-byte 평문으로 복호화한다.
        Map<String, byte[]> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(DEK_V1_CIPHERTEXT, DEK_V1_PLAINTEXT);
        ciphertextMap.put(HMAC_CIPHERTEXT, HMAC_PLAINTEXT);
        VaultTemplate vaultTemplate = mockVaultTemplateWith(ciphertextMap);

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT);

        VaultTransitPiiKeyVaultProperties props = new VaultTransitPiiKeyVaultProperties(
                KEY_NAME, 1, ENDPOINT_URL, TOKEN, encryptedKeys);

        // when — 생성자에서 모든 키를 즉시 복호화한다 (fail-fast).
        VaultTransitPiiKeyVault vault = new VaultTransitPiiKeyVault(props, vaultTemplate);

        // then — 활성 버전 1, DEK v1 과 HMAC 키 모두 캐시에서 즉시 반환된다.
        PiiKeyVault.ActiveKey activeKey = vault.getActiveDataEncryptionKey();
        assertThat(activeKey.version()).isEqualTo(1);
        assertThat(activeKey.key()).isNotNull();
        assertThat(activeKey.key().getAlgorithm()).isEqualTo("AES");
        assertThat(activeKey.key().getEncoded()).isEqualTo(DEK_V1_PLAINTEXT);

        SecretKey dekV1 = vault.getDataEncryptionKey(1);
        assertThat(dekV1).isNotNull();
        assertThat(dekV1.getEncoded()).isEqualTo(DEK_V1_PLAINTEXT);

        SecretKey hmacKey = vault.getHmacKey();
        assertThat(hmacKey).isNotNull();
        assertThat(hmacKey.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(hmacKey.getEncoded()).isEqualTo(HMAC_PLAINTEXT);
    }

    // ──────────────────────────────────────────────
    // Test 2: Vault 호출 실패 → 생성자에서 PiiKeyVaultException 던지기 (fail-fast)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("initWithVaultUnavailable_throwsPiiKeyVaultException")
    void initWithVaultUnavailable_throwsPiiKeyVaultException() {
        // given — Vault Transit decrypt 가 항상 VaultException 을 던지도록 설정 (서버 미가용 시뮬레이션).
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultTransitOperations transitOps = mock(VaultTransitOperations.class);
        when(vaultTemplate.opsForTransit()).thenReturn(transitOps);
        when(transitOps.decrypt(eq(KEY_NAME), any(Ciphertext.class)))
                .thenThrow(new VaultException("Connection refused: localhost:8200"));

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT);

        VaultTransitPiiKeyVaultProperties props = new VaultTransitPiiKeyVaultProperties(
                KEY_NAME, 1, ENDPOINT_URL, TOKEN, encryptedKeys);

        // when / then — 생성자에서 즉시 PiiKeyVaultException 으로 래핑되어 던져진다 (D4: fail-fast).
        assertThatThrownBy(() -> new VaultTransitPiiKeyVault(props, vaultTemplate))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("Vault Transit"); // 메시지에 'Vault Transit' 키워드 포함 (운영 디버깅용).
    }

    // ──────────────────────────────────────────────
    // Test 3: 미등록 버전 조회 시 PiiKeyVaultException
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getDataEncryptionKey_unknownVersion_throwsPiiKeyVaultException")
    void getDataEncryptionKey_unknownVersion_throwsPiiKeyVaultException() {
        // given — dek-v1 과 hmac 만 정상 등록한 vault 를 구성한다 (activeVersion=1).
        Map<String, byte[]> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(DEK_V1_CIPHERTEXT, DEK_V1_PLAINTEXT);
        ciphertextMap.put(HMAC_CIPHERTEXT, HMAC_PLAINTEXT);
        VaultTemplate vaultTemplate = mockVaultTemplateWith(ciphertextMap);

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT);

        VaultTransitPiiKeyVaultProperties props = new VaultTransitPiiKeyVaultProperties(
                KEY_NAME, 1, ENDPOINT_URL, TOKEN, encryptedKeys);
        VaultTransitPiiKeyVault vault = new VaultTransitPiiKeyVault(props, vaultTemplate);

        // when / then — 미등록 버전 999 조회 시 명확한 예외가 발생한다.
        assertThatThrownBy(() -> vault.getDataEncryptionKey(999))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("999");
    }

    // ──────────────────────────────────────────────
    // Test 4: HMAC 키가 HmacSHA256 알고리즘으로 노출됨
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getHmacKey_returnsHmacSha256Key")
    void getHmacKey_returnsHmacKey() {
        // given — dek-v1 과 hmac 모두 정상 복호화된 vault.
        Map<String, byte[]> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(DEK_V1_CIPHERTEXT, DEK_V1_PLAINTEXT);
        ciphertextMap.put(HMAC_CIPHERTEXT, HMAC_PLAINTEXT);
        VaultTemplate vaultTemplate = mockVaultTemplateWith(ciphertextMap);

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT);

        VaultTransitPiiKeyVaultProperties props = new VaultTransitPiiKeyVaultProperties(
                KEY_NAME, 1, ENDPOINT_URL, TOKEN, encryptedKeys);

        // when
        VaultTransitPiiKeyVault vault = new VaultTransitPiiKeyVault(props, vaultTemplate);
        SecretKey hmacKey = vault.getHmacKey();

        // then — HMAC 키는 non-null 이며 알고리즘은 HmacSHA256 이고, raw bytes 는 평문과 일치한다.
        assertThat(hmacKey).isNotNull();
        assertThat(hmacKey.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(hmacKey.getEncoded()).isEqualTo(HMAC_PLAINTEXT);
    }
}
