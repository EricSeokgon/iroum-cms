package kr.co.ircp.cms.domain.security.pii.kms;

import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AwsKmsPiiKeyVault LocalStack 통합 테스트.
 *
 * <p>SPEC-CMS-SECURITY-PII-KMS-001 — 실제 KMS API 호출(LocalStack)을 통한 통합 검증.
 *
 * <p>검증 시나리오:
 * <ul>
 *     <li>LocalStack KMS에 CMK 생성</li>
 *     <li>32-byte DEK / HMAC 키 로컬 생성 후 CMK로 암호화</li>
 *     <li>암호문을 Spring properties로 주입하여 vault 초기화</li>
 *     <li>vault가 생성자에서 복호화 후 캐시한 키를 노출하는지 확인</li>
 *     <li>DEK round-trip(AES-GCM 암호화/복호화) 정상 동작</li>
 * </ul>
 *
 * <p>실행 환경:
 * - Docker 필수 (LocalStack 3.3 + PostgreSQL 컨테이너).
 * - Docker 미설치 시 SKIP 처리 (AbstractIntegrationTest.assumeDockerAvailable 규약).
 */
// @MX:NOTE: [AUTO] LocalStack KMS 통합 테스트 — AbstractIntegrationTest 상속 (Postgres + LocalStack 모두 필요)
// @MX:SPEC: SPEC-CMS-SECURITY-PII-KMS-001
@SpringBootTest(properties = {
        "pii.keyvault.provider=aws-kms",
        "pii.keyvault.aws-kms.region=us-east-1"
})
@DisplayName("AwsKmsPiiKeyVault LocalStack 통합 테스트")
class AwsKmsPiiKeyVaultIT extends AbstractIntegrationTest {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    // Singleton Container Pattern — Ryuk이 JVM 종료 시 정리.
    // Docker 미설치 환경: LOCALSTACK = null → assumeLocalStackRunning이 SKIP 처리.
    static final LocalStackContainer LOCALSTACK;
    static final String CREATED_KEY_ID;
    static final String DEK_V1_CIPHERTEXT_B64;
    static final String HMAC_CIPHERTEXT_B64;

    static {
        // AWS SDK 기본 자격증명 체인 — 환경변수/시스템 프로퍼티 없으면 credential 조회 실패.
        // LocalStack은 비어있지 않은 임의 자격증명을 수락하므로 더미값으로 충분하다.
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");

        LocalStackContainer container = null;
        String keyId = null;
        String dekCipher = null;
        String hmacCipher = null;
        try {
            container = new LocalStackContainer(
                    DockerImageName.parse("localstack/localstack:3.3"))
                    .withServices(LocalStackContainer.Service.KMS);
            container.start();

            // LocalStack 부트스트랩: CMK 생성 + DEK/HMAC 키 암호화.
            try (KmsClient bootstrapClient = buildKmsClient(container)) {
                CreateKeyResponse createKey = bootstrapClient.createKey(b -> b
                        .description("PII KEK for AwsKmsPiiKeyVaultIT")
                        .keyUsage("ENCRYPT_DECRYPT"));
                keyId = createKey.keyMetadata().keyId();

                // 32-byte 랜덤 DEK 평문 생성.
                byte[] dekPlaintext = new byte[32];
                byte[] hmacPlaintext = new byte[32];
                SecureRandom random = new SecureRandom();
                random.nextBytes(dekPlaintext);
                random.nextBytes(hmacPlaintext);

                final String finalKeyId = keyId;
                EncryptResponse dekEnc = bootstrapClient.encrypt(b -> b
                        .keyId(finalKeyId)
                        .plaintext(SdkBytes.fromByteArray(dekPlaintext)));
                EncryptResponse hmacEnc = bootstrapClient.encrypt(b -> b
                        .keyId(finalKeyId)
                        .plaintext(SdkBytes.fromByteArray(hmacPlaintext)));

                dekCipher = Base64.getEncoder().encodeToString(dekEnc.ciphertextBlob().asByteArray());
                hmacCipher = Base64.getEncoder().encodeToString(hmacEnc.ciphertextBlob().asByteArray());
            }
        } catch (Exception e) {
            // Docker 미설치 환경 — assumeLocalStackRunning 가 SKIP 처리.
            container = null;
            keyId = null;
            dekCipher = null;
            hmacCipher = null;
        }
        LOCALSTACK = container;
        CREATED_KEY_ID = keyId;
        DEK_V1_CIPHERTEXT_B64 = dekCipher;
        HMAC_CIPHERTEXT_B64 = hmacCipher;
    }

    /** LocalStack 엔드포인트와 더미 자격증명으로 KmsClient를 구성한다. */
    private static KmsClient buildKmsClient(LocalStackContainer container) {
        return KmsClient.builder()
                .endpointOverride(container.getEndpointOverride(LocalStackContainer.Service.KMS))
                .region(Region.of(container.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(container.getAccessKey(), container.getSecretKey())))
                .build();
    }

    // assumeDockerAvailable()는 AbstractIntegrationTest에서 이미 PostgreSQL 존재를 체크한다.
    // LocalStack은 별도 @BeforeAll로 추가 검사 (이름 충돌 방지).
    @BeforeAll
    static void assumeLocalStackRunning() {
        Assumptions.assumeTrue(
                LOCALSTACK != null && LOCALSTACK.isRunning(),
                "LocalStack KMS 컨테이너 미기동 — AwsKmsPiiKeyVaultIT 건너뜀"
        );
    }

    @DynamicPropertySource
    static void registerKmsProperties(DynamicPropertyRegistry registry) {
        if (LOCALSTACK != null && LOCALSTACK.isRunning()) {
            URI endpoint = LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.KMS);
            registry.add("pii.keyvault.aws-kms.key-id", () -> CREATED_KEY_ID);
            registry.add("pii.keyvault.aws-kms.active-version", () -> "1");
            registry.add("pii.keyvault.aws-kms.endpoint-override", endpoint::toString);
            registry.add("pii.keyvault.aws-kms.encrypted-keys.dek-v1", () -> DEK_V1_CIPHERTEXT_B64);
            registry.add("pii.keyvault.aws-kms.encrypted-keys.hmac", () -> HMAC_CIPHERTEXT_B64);
            // AWS SDK 자격증명 — LocalStack 더미 키.
            registry.add("aws.accessKeyId", LOCALSTACK::getAccessKey);
            registry.add("aws.secretAccessKey", LOCALSTACK::getSecretKey);
        }
    }

    @Autowired
    private PiiKeyVault piiKeyVault;

    // ──────────────────────────────────────────────
    // Test 1: LocalStack KMS 기반 초기화 — 모든 키 복호화
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenLocalStackKms_whenInit_thenDecryptsAllKeys")
    void givenLocalStackKms_whenInit_thenDecryptsAllKeys() {
        // Spring이 application context 로드 시 AwsKmsPiiKeyVault가 생성자에서 LocalStack KMS를 호출해
        // dek-v1과 hmac을 복호화 후 in-memory 캐시한 상태이다.

        // then — 주입된 PiiKeyVault가 AwsKmsPiiKeyVault 인스턴스여야 한다.
        assertThat(piiKeyVault).isInstanceOf(AwsKmsPiiKeyVault.class);

        // 활성 DEK 버전이 1로 노출된다.
        PiiKeyVault.ActiveKey activeKey = piiKeyVault.getActiveDataEncryptionKey();
        assertThat(activeKey.version()).isEqualTo(1);
        assertThat(activeKey.key()).isNotNull();
        assertThat(activeKey.key().getEncoded()).hasSize(32); // AES-256.

        // HMAC 키도 정상 노출된다.
        SecretKey hmacKey = piiKeyVault.getHmacKey();
        assertThat(hmacKey).isNotNull();
        assertThat(hmacKey.getEncoded()).hasSize(32);
    }

    // ──────────────────────────────────────────────
    // Test 2: 복호화된 DEK로 AES-GCM round-trip 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenValidKey_whenDecrypt_thenRoundtripSucceeds")
    void givenValidKey_whenDecrypt_thenRoundtripSucceeds() throws Exception {
        // given — vault에서 활성 DEK를 가져온다.
        SecretKey dek = piiKeyVault.getDataEncryptionKey(1);
        byte[] plaintext = "민감정보-round-trip-테스트".getBytes(StandardCharsets.UTF_8);

        // when — AES-GCM으로 암호화 후 동일 키로 복호화한다 (PII 암호화 패턴 시뮬레이션).
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher encryptCipher = Cipher.getInstance(AES_GCM);
        encryptCipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = encryptCipher.doFinal(plaintext);

        Cipher decryptCipher = Cipher.getInstance(AES_GCM);
        decryptCipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] decrypted = decryptCipher.doFinal(ciphertext);

        // then — 평문이 정확히 복원되어야 한다 (KMS가 반환한 키가 유효한 AES-256 키임을 증명).
        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("민감정보-round-trip-테스트");
    }

    // ──────────────────────────────────────────────
    // Test 3: 미등록 버전 조회 시 PiiKeyVaultException
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenWrongVersion_whenGetDek_thenThrowsPiiKeyVaultException")
    void givenWrongVersion_whenGetDek_thenThrowsPiiKeyVaultException() {
        // when / then — encrypted-keys에 'dek-v99'가 없으므로 즉시 예외가 던져진다.
        assertThatThrownBy(() -> piiKeyVault.getDataEncryptionKey(99))
                .isInstanceOf(PiiKeyVaultException.class)
                .hasMessageContaining("99");
    }
}
