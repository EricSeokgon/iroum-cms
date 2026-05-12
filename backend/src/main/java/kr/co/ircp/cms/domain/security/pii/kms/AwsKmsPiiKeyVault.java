package kr.co.ircp.cms.domain.security.pii.kms;

import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsClientBuilder;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AWS KMS 기반 PiiKeyVault 구현체.
 *
 * <p>설정 (application.yml or env):
 * <pre>
 * pii:
 *   keyvault:
 *     provider: aws-kms
 *     aws-kms:
 *       key-id: arn:aws:kms:ap-northeast-2:123456789:key/your-cmk-id
 *       region: ap-northeast-2
 *       active-version: 1
 *       endpoint-override: # LocalStack 사용 시: http://localhost:4566
 *       encrypted-keys:
 *         dek-v1: BASE64_KMS_ENCRYPTED_AES_KEY_V1
 *         hmac: BASE64_KMS_ENCRYPTED_HMAC_KEY
 * </pre>
 *
 * <p>동작 방식:
 * 1. 부팅 시 KmsClient 생성 → 모든 암호화된 키 복호화 → 메모리 캐시
 * 2. 캐시된 키로 PiiKeyVault 인터페이스 메서드 처리
 * 3. KmsClient는 초기화 후 close (키는 메모리 보관)
 *
 * <p>키 매핑 규칙:
 * - "dek-v1" → version 1 DEK (AES)
 * - "dek-v2" → version 2 DEK (AES)
 * - "hmac"   → HMAC-SHA256 키
 *
 * <p>Fail-fast 정책: 활성 버전 누락, KMS 복호화 실패 시 부팅 시점에 예외 발생.
 *
 * @MX:ANCHOR: [AUTO] AwsKmsPiiKeyVault — PiiKeyVault 구현체 (fan_in >= 3 예상)
 * @MX:REASON: KMS 기반 DEK 관리 단일 진입점
 * @MX:SPEC: SPEC-CMS-SECURITY-PII-KMS-001
 */
@Component
@ConditionalOnProperty(name = "pii.keyvault.provider", havingValue = "aws-kms")
@EnableConfigurationProperties(AwsKmsPiiKeyVaultProperties.class)
public class AwsKmsPiiKeyVault implements PiiKeyVault {

    private static final String AES_ALGORITHM = "AES";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String DEK_PREFIX = "dek-v";
    private static final String HMAC_KEY_NAME = "hmac";

    private final int activeVersion;
    private final Map<Integer, SecretKey> dataKeys;
    private final SecretKey hmacKey;

    @Autowired
    public AwsKmsPiiKeyVault(AwsKmsPiiKeyVaultProperties properties) {
        this(properties, buildKmsClient(properties));
    }

    /**
     * 테스트 전용 패키지-프라이빗 생성자 — KmsClient를 외부에서 주입 (Mockito 지원).
     *
     * <p>운영 코드는 {@link #AwsKmsPiiKeyVault(AwsKmsPiiKeyVaultProperties)} 를 사용한다.
     */
    AwsKmsPiiKeyVault(AwsKmsPiiKeyVaultProperties properties, KmsClient kmsClient) {
        this.activeVersion = properties.activeVersion();
        this.dataKeys = new ConcurrentHashMap<>();

        Map<String, String> encryptedKeys = properties.encryptedKeys();
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: 암호화된 키 맵(pii.keyvault.aws-kms.encrypted-keys)이 비어있습니다"
            );
        }

        SecretKey resolvedHmacKey = null;

        // KMS 복호화는 부팅 시점 1회만 수행 → try-with-resources 로 클라이언트 정리
        try (KmsClient client = kmsClient) {
            for (Map.Entry<String, String> entry : encryptedKeys.entrySet()) {
                String name = entry.getKey();
                String ciphertextBase64 = entry.getValue();

                if (ciphertextBase64 == null || ciphertextBase64.isBlank()) {
                    throw new PiiKeyVaultException(
                            "AwsKmsPiiKeyVault: 키 '" + name + "' 의 ciphertext 가 비어있습니다"
                    );
                }

                byte[] plaintext = decryptViaKms(client, name, ciphertextBase64);

                if (HMAC_KEY_NAME.equals(name)) {
                    if (plaintext.length < 32) {
                        throw new PiiKeyVaultException(
                                "AwsKmsPiiKeyVault: HMAC 키 길이 부족 (필요: 32+ bytes, 실제: "
                                        + plaintext.length + ")"
                        );
                    }
                    resolvedHmacKey = new SecretKeySpec(plaintext, HMAC_ALGORITHM);
                } else if (name.startsWith(DEK_PREFIX)) {
                    int version = parseDekVersion(name);
                    if (plaintext.length != 32) {
                        throw new PiiKeyVaultException(
                                "AwsKmsPiiKeyVault: DEK v" + version + " 길이가 올바르지 않습니다 "
                                        + "(필요: 32 bytes / 256 bits, 실제: " + plaintext.length + " bytes)"
                        );
                    }
                    this.dataKeys.put(version, new SecretKeySpec(plaintext, AES_ALGORITHM));
                } else {
                    throw new PiiKeyVaultException(
                            "AwsKmsPiiKeyVault: 알 수 없는 키 이름 '" + name
                                    + "' (허용: 'dek-v{N}' 또는 'hmac')"
                    );
                }
            }
        } catch (KmsException e) {
            // KMS API 호출 실패 — 부팅 차단
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: KMS 키 복호화 실패 — " + e.getMessage(), e
            );
        }
        if (this.dataKeys.isEmpty()) {
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: DEK(dek-v*) 가 하나도 설정되지 않았습니다"
            );
        }
        if (!this.dataKeys.containsKey(activeVersion)) {
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: 활성 키 버전 " + activeVersion + " 에 해당하는 DEK 가 없습니다"
            );
        }
        if (resolvedHmacKey == null) {
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: HMAC 키('hmac')가 encrypted-keys 에 설정되지 않았습니다"
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
                    "AwsKmsPiiKeyVault: 키 버전 " + version + " 이 존재하지 않습니다"
            );
        }
        return key;
    }

    @Override
    public SecretKey getHmacKey() {
        return hmacKey;
    }

    /** 운영용 KmsClient 빌더 — endpointOverride 지원 (LocalStack 등). */
    private static KmsClient buildKmsClient(AwsKmsPiiKeyVaultProperties properties) {
        KmsClientBuilder builder = KmsClient.builder()
                .region(Region.of(properties.region()));
        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointOverride()));
        }
        return builder.build();
    }

    /**
     * KMS DecryptRequest 실행 후 평문 바이트 반환.
     *
     * @param kmsClient        KMS 클라이언트
     * @param name             키 이름 (오류 메시지용)
     * @param ciphertextBase64 base64 인코딩된 KMS ciphertext
     * @return 복호화된 평문 바이트
     */
    private byte[] decryptViaKms(KmsClient kmsClient, String name, String ciphertextBase64) {
        byte[] ciphertext;
        try {
            ciphertext = Base64.getDecoder().decode(ciphertextBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: 키 '" + name + "' base64 디코딩 실패", e
            );
        }

        DecryptRequest request = DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
                .build();
        DecryptResponse response = kmsClient.decrypt(request);
        return response.plaintext().asByteArray();
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
                        "AwsKmsPiiKeyVault: DEK 버전은 1 이상이어야 합니다 (입력: " + name + ")"
                );
            }
            return version;
        } catch (NumberFormatException e) {
            throw new PiiKeyVaultException(
                    "AwsKmsPiiKeyVault: DEK 키 이름 '" + name + "' 의 버전 번호 파싱 실패", e
            );
        }
    }
}
