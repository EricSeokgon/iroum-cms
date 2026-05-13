package kr.co.ircp.cms.domain.security.pii.kms;

import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AwsKmsPiiKeyVault 단위 테스트 (MockKmsClient).
 *
 * <p>SPEC-CMS-SECURITY-PII-KMS-001 — AWS KMS KEK-DEK 패턴 검증.
 *
 * <p>테스트 전략:
 * - {@link KmsClient}를 Mockito로 모킹하여 외부 의존성 제거.
 * - 생성자 시점에 모든 암호화된 키를 복호화하고 in-memory 캐시하는 fail-fast 동작 검증.
 * - 다중 DEK 버전(dek-v1, dek-v2)과 HMAC 키 분리 캐싱 검증.
 *
 * <p>구현 노트(구현 에이전트에게):
 * 본 테스트는 패키지-프라이빗 테스트 전용 생성자를 가정한다:
 * <pre>
 *   AwsKmsPiiKeyVault(AwsKmsPiiKeyVaultProperties props, KmsClient kmsClient) { ... }
 * </pre>
 * 운영용 public 생성자는 내부적으로 {@code KmsClient.builder()...build()}로 클라이언트를 빌드하고,
 * 그 결과를 본 패키지-프라이빗 생성자에 위임하는 형태가 권장된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AwsKmsPiiKeyVault 단위 테스트 (MockKmsClient)")
class AwsKmsPiiKeyVaultTest {

    private static final String KEY_ID = "arn:aws:kms:us-east-1:000000000000:key/test-cmk";
    private static final String REGION = "us-east-1";

    // 32-byte 결정적 평문 키들 — 각각 다른 byte 패턴으로 키 분리 검증 가능.
    private static final byte[] DEK_V1_PLAINTEXT = filled((byte) 0x11, 32);
    private static final byte[] DEK_V2_PLAINTEXT = filled((byte) 0x22, 32);
    private static final byte[] HMAC_PLAINTEXT = filled((byte) 0x33, 32);

    // 암호문은 어떤 값이든 무방 — Mock이 평문을 반환하므로 구분용 마커 역할만 한다.
    private static final String DEK_V1_CIPHERTEXT_B64 = Base64.getEncoder().encodeToString("CIPHERTEXT-DEK-V1".getBytes());
    private static final String DEK_V2_CIPHERTEXT_B64 = Base64.getEncoder().encodeToString("CIPHERTEXT-DEK-V2".getBytes());
    private static final String HMAC_CIPHERTEXT_B64 = Base64.getEncoder().encodeToString("CIPHERTEXT-HMAC".getBytes());

    private static byte[] filled(byte value, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = value;
        }
        return result;
    }

    /** ciphertext base64 → 평문 byte[] 매핑으로 Mock decrypt 응답을 구성한다. */
    private static KmsClient mockKmsClientWith(Map<String, byte[]> ciphertextToPlaintext) {
        KmsClient kmsClient = mock(KmsClient.class);
        when(kmsClient.decrypt(any(DecryptRequest.class))).thenAnswer(invocation -> {
            DecryptRequest req = invocation.getArgument(0);
            // 요청의 ciphertextBlob을 base64로 변환하여 매핑 키와 비교한다.
            String inputB64 = Base64.getEncoder().encodeToString(req.ciphertextBlob().asByteArray());
            byte[] plaintext = ciphertextToPlaintext.get(inputB64);
            if (plaintext == null) {
                throw KmsException.builder()
                        .message("Mock: 등록되지 않은 ciphertext 요청 — " + inputB64)
                        .build();
            }
            return DecryptResponse.builder()
                    .plaintext(SdkBytes.fromByteArray(plaintext))
                    .build();
        });
        return kmsClient;
    }

    // ──────────────────────────────────────────────
    // Test 1: 정상 초기화 — 모든 키 복호화 + 캐싱
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenValidEncryptedKeys_whenInit_thenDecryptsAndCachesAllKeys")
    void givenValidEncryptedKeys_whenInit_thenDecryptsAndCachesAllKeys() {
        // given — KMS는 dek-v1과 hmac의 암호문을 각각 32-byte 평문으로 복호화한다.
        Map<String, byte[]> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(DEK_V1_CIPHERTEXT_B64, DEK_V1_PLAINTEXT);
        ciphertextMap.put(HMAC_CIPHERTEXT_B64, HMAC_PLAINTEXT);
        KmsClient kmsClient = mockKmsClientWith(ciphertextMap);

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT_B64);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT_B64);

        AwsKmsPiiKeyVaultProperties props = new AwsKmsPiiKeyVaultProperties(
                KEY_ID, REGION, 1, null, encryptedKeys);

        // when — 생성자에서 모든 키를 즉시 복호화한다 (fail-fast).
        AwsKmsPiiKeyVault vault = new AwsKmsPiiKeyVault(props, kmsClient);

        // then — 활성 버전 1, DEK v1과 HMAC 키 모두 캐시에서 즉시 반환된다.
        PiiKeyVault.ActiveKey activeKey = vault.getActiveDataEncryptionKey();
        assertThat(activeKey.version()).isEqualTo(1);
        assertThat(activeKey.key()).isNotNull();
        assertThat(activeKey.key().getAlgorithm()).isEqualTo("AES");

        SecretKey dekV1 = vault.getDataEncryptionKey(1);
        assertThat(dekV1).isNotNull();
        assertThat(dekV1.getEncoded()).isEqualTo(DEK_V1_PLAINTEXT);

        SecretKey hmacKey = vault.getHmacKey();
        assertThat(hmacKey).isNotNull();
        assertThat(hmacKey.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(hmacKey.getEncoded()).isEqualTo(HMAC_PLAINTEXT);
    }

    // ──────────────────────────────────────────────
    // Test 2: KMS 복호화 실패 → 생성자에서 PiiKeyVaultException 던지기 (fail-fast)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenKmsDecryptFails_whenInit_thenThrowsPiiKeyVaultException")
    void givenKmsDecryptFails_whenInit_thenThrowsPiiKeyVaultException() {
        // given — KMS.decrypt 호출이 항상 KmsException을 던지도록 설정한다.
        KmsClient kmsClient = mock(KmsClient.class);
        when(kmsClient.decrypt(any(DecryptRequest.class)))
                .thenThrow(KmsException.builder()
                        .message("AccessDenied: Invalid CMK")
                        .build());

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT_B64);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT_B64);

        AwsKmsPiiKeyVaultProperties props = new AwsKmsPiiKeyVaultProperties(
                KEY_ID, REGION, 1, null, encryptedKeys);

        // when / then — 생성자에서 즉시 PiiKeyVaultException으로 래핑되어 던져진다.
        assertThatThrownBy(() -> new AwsKmsPiiKeyVault(props, kmsClient))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("KMS"); // 메시지에 'KMS' 키워드가 포함되어야 한다 (운영 디버깅용).
    }

    // ──────────────────────────────────────────────
    // Test 3: 미등록 버전 조회 시 PiiKeyVaultException
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenUnknownVersion_whenGetDek_thenThrowsPiiKeyVaultException")
    void givenUnknownVersion_whenGetDek_thenThrowsPiiKeyVaultException() {
        // given — dek-v1과 hmac만 정상 등록한 vault를 구성한다 (activeVersion=1).
        Map<String, byte[]> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(DEK_V1_CIPHERTEXT_B64, DEK_V1_PLAINTEXT);
        ciphertextMap.put(HMAC_CIPHERTEXT_B64, HMAC_PLAINTEXT);
        KmsClient kmsClient = mockKmsClientWith(ciphertextMap);

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT_B64);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT_B64);

        AwsKmsPiiKeyVaultProperties props = new AwsKmsPiiKeyVaultProperties(
                KEY_ID, REGION, 1, null, encryptedKeys);
        AwsKmsPiiKeyVault vault = new AwsKmsPiiKeyVault(props, kmsClient);

        // when / then — 미등록 버전 99 조회 시 명확한 예외가 발생한다.
        assertThatThrownBy(() -> vault.getDataEncryptionKey(99))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("99");
    }

    // ──────────────────────────────────────────────
    // Test 4: 다중 버전 — getDek(1)과 getDek(2)이 서로 다른 키 반환
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenMultipleVersions_whenGetDek_thenReturnsCorrectKey")
    void givenMultipleVersions_whenGetDek_thenReturnsCorrectKey() {
        // given — dek-v1, dek-v2, hmac 세 개의 키가 각각 다른 평문으로 복호화된다.
        Map<String, byte[]> ciphertextMap = new LinkedHashMap<>();
        ciphertextMap.put(DEK_V1_CIPHERTEXT_B64, DEK_V1_PLAINTEXT);
        ciphertextMap.put(DEK_V2_CIPHERTEXT_B64, DEK_V2_PLAINTEXT);
        ciphertextMap.put(HMAC_CIPHERTEXT_B64, HMAC_PLAINTEXT);
        KmsClient kmsClient = mockKmsClientWith(ciphertextMap);

        Map<String, String> encryptedKeys = new LinkedHashMap<>();
        encryptedKeys.put("dek-v1", DEK_V1_CIPHERTEXT_B64);
        encryptedKeys.put("dek-v2", DEK_V2_CIPHERTEXT_B64);
        encryptedKeys.put("hmac", HMAC_CIPHERTEXT_B64);

        // activeVersion=2 → 활성 키는 v2여야 한다.
        AwsKmsPiiKeyVaultProperties props = new AwsKmsPiiKeyVaultProperties(
                KEY_ID, REGION, 2, null, encryptedKeys);

        // when
        AwsKmsPiiKeyVault vault = new AwsKmsPiiKeyVault(props, kmsClient);

        SecretKey dekV1 = vault.getDataEncryptionKey(1);
        SecretKey dekV2 = vault.getDataEncryptionKey(2);
        PiiKeyVault.ActiveKey activeKey = vault.getActiveDataEncryptionKey();

        // then — v1과 v2는 서로 다른 raw bytes를 가져야 한다.
        assertThat(dekV1.getEncoded()).isEqualTo(DEK_V1_PLAINTEXT);
        assertThat(dekV2.getEncoded()).isEqualTo(DEK_V2_PLAINTEXT);
        assertThat(dekV1.getEncoded()).isNotEqualTo(dekV2.getEncoded());

        // 활성 키는 v2 = DEK_V2_PLAINTEXT.
        assertThat(activeKey.version()).isEqualTo(2);
        assertThat(activeKey.key().getEncoded()).isEqualTo(DEK_V2_PLAINTEXT);
    }
}
