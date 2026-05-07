package kr.co.ircp.cms.domain.security.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalEnvPiiKeyVault TDD 테스트.
 *
 * <p>SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-004: 키 관리 인터페이스 + LocalEnvPiiKeyVault 구현.
 *
 * <p>테스트 영역:
 * - 생성자: v1 단독 / v1+v2 다중버전 / 누락 / 활성버전 불일치 / 키 길이·base64 오류
 * - getActiveDataEncryptionKey: 활성 버전 + 키 반환
 * - getDataEncryptionKey: 등록 버전 조회 / 미등록 버전 예외 / 다중 버전 분리
 * - getHmacKey: HMAC 키 반환
 */
@DisplayName("LocalEnvPiiKeyVault TDD 테스트 (REQ-PII-EMAIL-004)")
class LocalEnvPiiKeyVaultTest {

    // 32-byte (256-bit) 테스트 키들. AES-256 요건을 만족하는 길이.
    private static final byte[] V1_KEY_BYTES = bytesFilledWith((byte) 0x01, 32);
    private static final byte[] V2_KEY_BYTES = bytesFilledWith((byte) 0x02, 32);
    private static final byte[] HMAC_KEY_BYTES = bytesFilledWith((byte) 0x03, 32);

    private static final String VALID_KEY_V1_BASE64 = Base64.getEncoder().encodeToString(V1_KEY_BYTES);
    private static final String VALID_KEY_V2_BASE64 = Base64.getEncoder().encodeToString(V2_KEY_BYTES);
    private static final String VALID_HMAC_BASE64 = Base64.getEncoder().encodeToString(HMAC_KEY_BYTES);

    /** 동일한 byte 값으로 채운 길이 N의 byte[] 생성 (테스트 결정성 확보). */
    private static byte[] bytesFilledWith(byte value, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = value;
        }
        return result;
    }

    // ──────────────────────────────────────────────
    // A. Construction success
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("생성 — v1 키만 등록되었을 때 active=1로 정상 초기화")
    void constructor_v1Only_initializesSuccessfully() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", VALID_HMAC_BASE64
        );

        assertThat(vault.getActiveDataEncryptionKey().version()).isEqualTo(1);
        assertThat(vault.getActiveDataEncryptionKey().key()).isNotNull();
    }

    @Test
    @DisplayName("생성 — v1 + v2 키 모두 등록 + active=2 → 다중 버전 초기화 성공")
    void constructor_v1AndV2_active2_initializesSuccessfully() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                2, VALID_KEY_V1_BASE64, VALID_KEY_V2_BASE64, VALID_HMAC_BASE64
        );

        assertThat(vault.getActiveDataEncryptionKey().version()).isEqualTo(2);
        assertThat(vault.getDataEncryptionKey(1)).isNotNull();
        assertThat(vault.getDataEncryptionKey(2)).isNotNull();
    }

    // ──────────────────────────────────────────────
    // B. getActiveDataEncryptionKey
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getActiveDataEncryptionKey — 활성 키 + 버전을 ActiveKey 레코드로 반환")
    void getActiveDataEncryptionKey_returnsActiveKeyAndVersion() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", VALID_HMAC_BASE64
        );

        PiiKeyVault.ActiveKey active = vault.getActiveDataEncryptionKey();

        assertThat(active).isNotNull();
        assertThat(active.version()).isEqualTo(1);
        assertThat(active.key().getEncoded()).containsExactly(V1_KEY_BYTES);
        assertThat(active.key().getAlgorithm()).isEqualTo("AES");
    }

    @Test
    @DisplayName("getActiveDataEncryptionKey — v1+v2 등록 시 active=2의 키 반환")
    void getActiveDataEncryptionKey_v1AndV2_returnsV2WhenActive2() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                2, VALID_KEY_V1_BASE64, VALID_KEY_V2_BASE64, VALID_HMAC_BASE64
        );

        PiiKeyVault.ActiveKey active = vault.getActiveDataEncryptionKey();

        assertThat(active.version()).isEqualTo(2);
        assertThat(active.key().getEncoded()).containsExactly(V2_KEY_BYTES);
    }

    // ──────────────────────────────────────────────
    // C. getDataEncryptionKey(version)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getDataEncryptionKey — 등록된 v1 조회 시 키 반환")
    void getDataEncryptionKey_existingV1_returnsKey() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", VALID_HMAC_BASE64
        );

        SecretKey key = vault.getDataEncryptionKey(1);

        assertThat(key).isNotNull();
        assertThat(key.getEncoded()).containsExactly(V1_KEY_BYTES);
        assertThat(key.getAlgorithm()).isEqualTo("AES");
    }

    @Test
    @DisplayName("getDataEncryptionKey — 미등록 버전(v3) 조회 시 PiiKeyVaultException")
    void getDataEncryptionKey_unknownVersion_throwsException() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", VALID_HMAC_BASE64
        );

        assertThatThrownBy(() -> vault.getDataEncryptionKey(3))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("키 버전 3")
                .hasMessageContaining("존재하지 않습니다");
    }

    @Test
    @DisplayName("getDataEncryptionKey — v1+v2 등록 후 v1·v2 각각 정확한 키 반환 (버전별 분리)")
    void getDataEncryptionKey_v1AndV2_returnsDistinctKeysPerVersion() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                2, VALID_KEY_V1_BASE64, VALID_KEY_V2_BASE64, VALID_HMAC_BASE64
        );

        SecretKey v1 = vault.getDataEncryptionKey(1);
        SecretKey v2 = vault.getDataEncryptionKey(2);

        assertThat(v1.getEncoded()).containsExactly(V1_KEY_BYTES);
        assertThat(v2.getEncoded()).containsExactly(V2_KEY_BYTES);
        assertThat(v1.getEncoded()).isNotEqualTo(v2.getEncoded());
    }

    // ──────────────────────────────────────────────
    // D. getHmacKey
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getHmacKey — HMAC-SHA256 키 반환")
    void getHmacKey_returnsHmacKey() {
        LocalEnvPiiKeyVault vault = new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", VALID_HMAC_BASE64
        );

        SecretKey hmac = vault.getHmacKey();

        assertThat(hmac).isNotNull();
        assertThat(hmac.getEncoded()).containsExactly(HMAC_KEY_BYTES);
        assertThat(hmac.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    // ──────────────────────────────────────────────
    // E. Construction failure
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("생성 실패 — v1·v2 모두 빈 문자열 → PiiKeyVaultException (키 미설정)")
    void constructor_noKeysProvided_throwsException() {
        assertThatThrownBy(() -> new LocalEnvPiiKeyVault(
                1, "", "", VALID_HMAC_BASE64
        ))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("데이터 암호화 키가 하나도 설정되지 않았습니다");
    }

    @Test
    @DisplayName("생성 실패 — v1만 등록되었지만 active=2 → PiiKeyVaultException")
    void constructor_activeVersionNotInKeys_throwsException() {
        assertThatThrownBy(() -> new LocalEnvPiiKeyVault(
                2, VALID_KEY_V1_BASE64, "", VALID_HMAC_BASE64
        ))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("활성 키 버전 2")
                .hasMessageContaining("해당하는 키가 없습니다");
    }

    @Test
    @DisplayName("생성 실패 — HMAC 키 빈 문자열 → PiiKeyVaultException")
    void constructor_emptyHmacKey_throwsException() {
        assertThatThrownBy(() -> new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", ""
        ))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("HMAC 키가 설정되지 않았습니다");
    }

    @Test
    @DisplayName("생성 실패 — AES 키 31 bytes (잘못된 길이) → PiiKeyVaultException")
    void constructor_aesKeyWrongLength_throwsException() {
        // 31 bytes — AES-256 (32 bytes) 미만
        String shortKeyBase64 = Base64.getEncoder()
                .encodeToString(bytesFilledWith((byte) 0x04, 31));

        assertThatThrownBy(() -> new LocalEnvPiiKeyVault(
                1, shortKeyBase64, "", VALID_HMAC_BASE64
        ))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("키 v1")
                .hasMessageContaining("필요: 32 bytes")
                .hasMessageContaining("실제: 31 bytes");
    }

    @Test
    @DisplayName("생성 실패 — AES 키 base64 디코딩 실패 → PiiKeyVaultException")
    void constructor_aesKeyInvalidBase64_throwsException() {
        // 유효하지 않은 base64 문자 포함
        String invalidBase64 = "!!!not-valid-base64@@@";

        assertThatThrownBy(() -> new LocalEnvPiiKeyVault(
                1, invalidBase64, "", VALID_HMAC_BASE64
        ))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("base64 디코딩 실패");
    }

    @Test
    @DisplayName("생성 실패 — HMAC 키 16 bytes (너무 짧음) → PiiKeyVaultException")
    void constructor_hmacKeyTooShort_throwsException() {
        // 16 bytes — 최소 32 bytes 요건 위반
        String shortHmacBase64 = Base64.getEncoder()
                .encodeToString(bytesFilledWith((byte) 0x05, 16));

        assertThatThrownBy(() -> new LocalEnvPiiKeyVault(
                1, VALID_KEY_V1_BASE64, "", shortHmacBase64
        ))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("HMAC 키 길이 부족")
                .hasMessageContaining("실제: 16");
    }
}
