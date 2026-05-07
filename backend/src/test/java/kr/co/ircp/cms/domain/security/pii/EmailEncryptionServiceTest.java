package kr.co.ircp.cms.domain.security.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * EmailEncryptionService TDD 테스트.
 *
 * <p>SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-001/002/003.
 *
 * <p>테스트 영역 (17 cases):
 * <ul>
 *   <li>A. encrypt happy path (3): 정상 암호화 / IV 무작위성 / active 버전 사용</li>
 *   <li>B. encrypt edge cases (3): null/empty plaintext / KeyVault 예외 전파</li>
 *   <li>C. decrypt happy path (3): round-trip / 직접 복호화 / 다중 버전</li>
 *   <li>D. decrypt failure (3): 변조 ciphertext / null / 미등록 버전</li>
 *   <li>E. HMAC computation (5): deterministic / 차이 / 정규화 / null / 길이</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailEncryptionService TDD 테스트 (REQ-PII-EMAIL-001/002/003)")
class EmailEncryptionServiceTest {

    @Mock
    private PiiKeyVault keyVault;

    private EmailEncryptionService service;

    private SecretKey testAesKeyV1;
    private SecretKey testAesKeyV2;
    private SecretKey testHmacKey;

    @BeforeEach
    void setUp() {
        // 테스트용 결정적 키 (모든 테스트 round-trip이 같은 키로 작동)
        byte[] aesV1Bytes = filledBytes((byte) 0x11, 32);
        byte[] aesV2Bytes = filledBytes((byte) 0x22, 32);
        byte[] hmacBytes = filledBytes((byte) 0x33, 32);

        testAesKeyV1 = new SecretKeySpec(aesV1Bytes, "AES");
        testAesKeyV2 = new SecretKeySpec(aesV2Bytes, "AES");
        testHmacKey = new SecretKeySpec(hmacBytes, "HmacSHA256");

        // LENIENT — 일부 테스트는 keyVault.* 미사용. strict mode면 UnnecessaryStubbing 발생.
        when(keyVault.getActiveDataEncryptionKey())
                .thenReturn(new PiiKeyVault.ActiveKey(1, testAesKeyV1));
        when(keyVault.getDataEncryptionKey(1)).thenReturn(testAesKeyV1);
        when(keyVault.getDataEncryptionKey(2)).thenReturn(testAesKeyV2);
        when(keyVault.getHmacKey()).thenReturn(testHmacKey);

        service = new EmailEncryptionService(keyVault);
    }

    private static byte[] filledBytes(byte value, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = value;
        }
        return result;
    }

    // ──────────────────────────────────────────────
    // A. encrypt happy path
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("encrypt — 유효한 email은 EncryptedEmail (12-byte IV + 16-byte tag + ciphertext + keyVersion) 생성")
    void encrypt_validEmail_producesEncryptedEmail() {
        EncryptedEmail result = service.encrypt("user@example.com");

        assertThat(result).isNotNull();
        assertThat(result.ciphertext()).isNotEmpty();
        assertThat(result.iv()).hasSize(12);
        assertThat(result.tag()).hasSize(16);
        assertThat(result.keyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("encrypt — 동일 plaintext 2회 호출 시 IV/ciphertext가 매번 다름 (RISK-PII-05 IV 재사용 방지)")
    void encrypt_sameInput_producesDifferentCiphertext() {
        String email = "user@example.com";

        EncryptedEmail first = service.encrypt(email);
        EncryptedEmail second = service.encrypt(email);

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        // tag 또한 IV 영향으로 다름
        assertThat(first.tag()).isNotEqualTo(second.tag());
    }

    @Test
    @DisplayName("encrypt — active 키 버전이 2일 때 결과의 keyVersion=2")
    void encrypt_returnsActiveKeyVersion() {
        when(keyVault.getActiveDataEncryptionKey())
                .thenReturn(new PiiKeyVault.ActiveKey(2, testAesKeyV2));

        EncryptedEmail result = service.encrypt("user@example.com");

        assertThat(result.keyVersion()).isEqualTo(2);
    }

    // ──────────────────────────────────────────────
    // B. encrypt edge cases
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("encrypt — null plaintext → IllegalArgumentException")
    void encrypt_nullPlaintext_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintext email은 비어있을 수 없습니다");
    }

    @Test
    @DisplayName("encrypt — 빈 문자열 plaintext → IllegalArgumentException")
    void encrypt_emptyPlaintext_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.encrypt(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintext email은 비어있을 수 없습니다");
    }

    @Test
    @DisplayName("encrypt — KeyVault.getActiveDataEncryptionKey 실패 시 PiiKeyVaultException 전파")
    void encrypt_keyVaultThrows_propagatesException() {
        when(keyVault.getActiveDataEncryptionKey())
                .thenThrow(new PiiKeyVaultException("KeyVault 비활성"));

        assertThatThrownBy(() -> service.encrypt("user@example.com"))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("KeyVault 비활성");
    }

    // ──────────────────────────────────────────────
    // C. decrypt happy path (round-trip)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("encrypt → decrypt round-trip — 원본 email 복원")
    void encryptDecrypt_roundTrip_returnsOriginal() {
        String original = "user@example.com";

        EncryptedEmail encrypted = service.encrypt(original);
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("decrypt — 다양한 길이/문자의 email 모두 정확히 복원")
    void decrypt_validInput_returnsPlaintext() {
        String[] samples = {
                "a@b.co",
                "longer.email+tag@subdomain.example.com",
                "한글이메일@example.com",
                "u".repeat(100) + "@example.com"
        };

        for (String sample : samples) {
            EncryptedEmail encrypted = service.encrypt(sample);
            String decrypted = service.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(sample);
        }
    }

    @Test
    @DisplayName("decrypt — keyVersion=1로 암호화된 데이터는 keyVersion=1 키로 복호화 (다중 버전 분리)")
    void decrypt_keyVersion2_usesCorrectKey() {
        // active=1로 암호화
        EncryptedEmail v1Encrypted = service.encrypt("user@example.com");
        assertThat(v1Encrypted.keyVersion()).isEqualTo(1);

        // active를 2로 변경해도 v1 데이터는 v1 키로 복호화 가능해야 함
        when(keyVault.getActiveDataEncryptionKey())
                .thenReturn(new PiiKeyVault.ActiveKey(2, testAesKeyV2));

        String decrypted = service.decrypt(v1Encrypted);
        assertThat(decrypted).isEqualTo("user@example.com");
    }

    // ──────────────────────────────────────────────
    // D. decrypt failure
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("decrypt — ciphertext 변조 시 PiiKeyVaultException (RISK-PII-04 무결성 위반)")
    void decrypt_tamperedCiphertext_throwsPiiKeyVaultException() {
        EncryptedEmail original = service.encrypt("user@example.com");

        // ciphertext 1 byte 변조
        byte[] tampered = original.ciphertext().clone();
        tampered[0] = (byte) (tampered[0] ^ 0xFF);

        EncryptedEmail tamperedEmail = new EncryptedEmail(
                tampered, original.iv(), original.tag(), original.keyVersion()
        );

        assertThatThrownBy(() -> service.decrypt(tamperedEmail))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("무결성 위반");
    }

    @Test
    @DisplayName("decrypt — null EncryptedEmail → IllegalArgumentException")
    void decrypt_nullEncrypted_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encrypted는 null일 수 없습니다");
    }

    @Test
    @DisplayName("decrypt — 미등록 키 버전(v999) 조회 시 PiiKeyVaultException")
    void decrypt_unknownKeyVersion_throwsPiiKeyVaultException() {
        when(keyVault.getDataEncryptionKey(999))
                .thenThrow(new PiiKeyVaultException("키 버전 999이 존재하지 않습니다"));

        // 형상은 맞춰야 하므로 valid IV/tag/ciphertext 사용
        byte[] dummyCiphertext = new byte[]{1, 2, 3, 4};
        byte[] dummyIv = new byte[12];
        byte[] dummyTag = new byte[16];
        EncryptedEmail orphan = new EncryptedEmail(dummyCiphertext, dummyIv, dummyTag, 999);

        assertThatThrownBy(() -> service.decrypt(orphan))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("999");
    }

    // ──────────────────────────────────────────────
    // E. HMAC computation
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("computeHmac — 동일 input → 동일 output (deterministic)")
    void computeHmac_sameInput_returnsSameOutput() {
        String email = "user@example.com";

        String first = service.computeHmac(email);
        String second = service.computeHmac(email);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("computeHmac — 서로 다른 input → 서로 다른 output")
    void computeHmac_differentInput_returnsDifferentOutput() {
        String hmacA = service.computeHmac("a@example.com");
        String hmacB = service.computeHmac("b@example.com");

        assertThat(hmacA).isNotEqualTo(hmacB);
    }

    @Test
    @DisplayName("computeHmac — 대소문자/앞뒤 공백 정규화 → 동일 HMAC 결과")
    void computeHmac_caseAndWhitespaceNormalization() {
        String normalized = service.computeHmac("user@example.com");
        String upperWithSpaces = service.computeHmac("  USER@EXAMPLE.COM  ");
        String mixedCase = service.computeHmac("User@Example.Com");

        assertThat(upperWithSpaces).isEqualTo(normalized);
        assertThat(mixedCase).isEqualTo(normalized);
    }

    @Test
    @DisplayName("computeHmac — null input → IllegalArgumentException")
    void computeHmac_nullInput_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.computeHmac(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintext는 null일 수 없습니다");
    }

    @Test
    @DisplayName("computeHmac — 출력은 64 chars 소문자 hex (HMAC-SHA256 = 32 bytes = 64 hex chars)")
    void computeHmac_returns64HexChars() {
        String hmac = service.computeHmac("user@example.com");

        assertThat(hmac).hasSize(64);
        assertThat(hmac).matches("[0-9a-f]{64}");
    }

    // ──────────────────────────────────────────────
    // 부가: SecureRandom 인스턴스 정의가 정상인지 sanity check
    // (테스트 일관성을 위해 별도 random source 사용 안함)
    // ──────────────────────────────────────────────
    @SuppressWarnings("unused")
    private static final SecureRandom UNUSED_SANITY = new SecureRandom();
}
