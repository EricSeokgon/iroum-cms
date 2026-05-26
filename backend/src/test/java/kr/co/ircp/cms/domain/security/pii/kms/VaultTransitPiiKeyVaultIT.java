package kr.co.ircp.cms.domain.security.pii.kms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VaultTransitPiiKeyVault Testcontainers 통합 테스트 (HashiCorp Vault 1.17).
 *
 * <p>SPEC-CMS-SECURITY-PII-KMS-001 — 실제 Vault Transit API 호출(Testcontainers)을 통한 통합 검증.
 *
 * <p>검증 시나리오:
 * <ul>
 *     <li>Vault dev mode 컨테이너 부팅 (root token = test-token)</li>
 *     <li>{@code withInitCommand} 로 transit secrets engine 활성화 + named key 생성</li>
 *     <li>32-byte 랜덤 DEK / HMAC 평문 생성 → Vault Transit HTTP API 로 암호화 → ciphertext 획득</li>
 *     <li>{@code @DynamicPropertySource} 로 spring.cloud.vault.* 와 pii.keyvault.vault-transit.* 주입</li>
 *     <li>Spring Boot 컨텍스트 부팅 → VaultTransitPiiKeyVault 가 생성자에서 ciphertext 복호화 후 캐시</li>
 *     <li>주입된 PiiKeyVault 빈에서 활성 DEK / 명시 버전 / HMAC 키 조회 검증</li>
 * </ul>
 *
 * <p>실행 환경:
 * - Docker 필수 (Vault 1.17 + PostgreSQL 컨테이너).
 * - Docker 미설치 시 SKIP 처리 (AbstractIntegrationTest.assumeDockerAvailable + assumeVaultRunning).
 */
// @MX:NOTE: [AUTO] Testcontainers Vault 통합 테스트 — AbstractIntegrationTest 상속 (Postgres + Vault 동시 기동)
// @MX:SPEC: SPEC-CMS-SECURITY-PII-KMS-001
@SpringBootTest(properties = {
        "pii.keyvault.provider=vault-transit",
        "spring.cloud.vault.fail-fast=true",
        "spring.cloud.vault.config.lifecycle.enabled=false",
        // 시크릿/구성 백엔드는 사용하지 않음 — Transit API 만 호출하면 충분하다.
        "spring.cloud.vault.kv.enabled=false",
        "spring.cloud.vault.generic.enabled=false"
})
@DisplayName("VaultTransitPiiKeyVault Testcontainers 통합 테스트")
class VaultTransitPiiKeyVaultIT extends AbstractIntegrationTest {

    private static final String VAULT_TOKEN = "test-token";
    private static final String TRANSIT_KEY_NAME = "pii-dek";

    // Singleton Container Pattern — Ryuk 이 JVM 종료 시 정리.
    // Docker 미설치 환경: VAULT = null → assumeVaultRunning 이 SKIP 처리.
    static final VaultContainer<?> VAULT;
    static final String DEK_V1_CIPHERTEXT;
    static final String HMAC_CIPHERTEXT;

    static {
        VaultContainer<?> container = null;
        String dekCipher = null;
        String hmacCipher = null;
        try {
            // hashicorp/vault:1.17 — dev mode 자동 활성화 (root token = VAULT_TOKEN).
            // withInitCommand: 컨테이너 부팅 직후 CLI 로 transit secrets engine 활성화 + key 생성.
            container = new VaultContainer<>(DockerImageName.parse("hashicorp/vault:1.17"))
                    .withVaultToken(VAULT_TOKEN)
                    .withInitCommand(
                            "secrets enable transit",
                            "write -f transit/keys/" + TRANSIT_KEY_NAME
                    );
            container.start();

            // 32-byte 랜덤 DEK / HMAC 평문 생성 (실제 운영 키 시뮬레이션).
            byte[] dekPlaintext = new byte[32];
            byte[] hmacPlaintext = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(dekPlaintext);
            random.nextBytes(hmacPlaintext);

            // Vault Transit HTTP API 로 두 키를 암호화하여 ciphertext 문자열을 획득한다.
            String vaultUri = container.getHttpHostAddress(); // e.g. http://localhost:32768
            dekCipher = encryptViaVaultHttpApi(vaultUri, TRANSIT_KEY_NAME, dekPlaintext);
            hmacCipher = encryptViaVaultHttpApi(vaultUri, TRANSIT_KEY_NAME, hmacPlaintext);
        } catch (Exception e) {
            // Docker 미설치 또는 부팅 실패 — assumeVaultRunning 이 SKIP 처리.
            container = null;
            dekCipher = null;
            hmacCipher = null;
        }
        VAULT = container;
        DEK_V1_CIPHERTEXT = dekCipher;
        HMAC_CIPHERTEXT = hmacCipher;
    }

    /**
     * Vault Transit HTTP API 로 plaintext 를 암호화하여 ciphertext 문자열을 반환한다.
     *
     * <p>POST /v1/transit/encrypt/{key} body: {@code {"plaintext": "<base64>"}}
     * <p>응답: {@code {"data": {"ciphertext": "vault:v1:..."}}}
     */
    private static String encryptViaVaultHttpApi(String vaultUri, String keyName, byte[] plaintext) throws Exception {
        String base64Plaintext = Base64.getEncoder().encodeToString(plaintext);
        String requestBody = "{\"plaintext\":\"" + base64Plaintext + "\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultUri + "/v1/transit/encrypt/" + keyName))
                .header("X-Vault-Token", VAULT_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Vault Transit encrypt 실패 (status=" + response.statusCode() + "): " + response.body()
            );
        }

        JsonNode root = new ObjectMapper().readTree(response.body());
        String ciphertext = root.path("data").path("ciphertext").asText(null);
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalStateException("Vault Transit encrypt 응답에 ciphertext 가 없습니다: " + response.body());
        }
        return ciphertext;
    }

    // assumeDockerAvailable() 는 AbstractIntegrationTest 가 PostgreSQL 가용성을 체크한다.
    // Vault 컨테이너는 별도로 추가 검사 (이름 충돌 방지).
    @BeforeAll
    static void assumeVaultRunning() {
        Assumptions.assumeTrue(
                VAULT != null && VAULT.isRunning(),
                "Vault 컨테이너 미기동 — VaultTransitPiiKeyVaultIT 건너뜀"
        );
    }

    @DynamicPropertySource
    static void registerVaultProperties(DynamicPropertyRegistry registry) {
        if (VAULT != null && VAULT.isRunning()) {
            String vaultUri = VAULT.getHttpHostAddress();
            // Spring Cloud Vault — VaultTemplate 자동 구성을 위한 핵심 프로퍼티.
            registry.add("spring.cloud.vault.uri", () -> vaultUri);
            registry.add("spring.cloud.vault.token", () -> VAULT_TOKEN);
            registry.add("spring.cloud.vault.authentication", () -> "TOKEN");
            // VaultTransitPiiKeyVault 설정.
            registry.add("pii.keyvault.vault-transit.key-name", () -> TRANSIT_KEY_NAME);
            registry.add("pii.keyvault.vault-transit.active-version", () -> "1");
            registry.add("pii.keyvault.vault-transit.endpoint-url", () -> vaultUri);
            registry.add("pii.keyvault.vault-transit.token", () -> VAULT_TOKEN);
            registry.add("pii.keyvault.vault-transit.encrypted-keys.dek-v1", () -> DEK_V1_CIPHERTEXT);
            registry.add("pii.keyvault.vault-transit.encrypted-keys.hmac", () -> HMAC_CIPHERTEXT);
        }
    }

    @Autowired
    private PiiKeyVault piiKeyVault;

    // ──────────────────────────────────────────────
    // Test 1: 활성 DEK 가 version 1 로 노출되고 AES 키로 캐싱되어 있음
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getActiveDataEncryptionKey_returnsVersion1")
    void getActiveDataEncryptionKey_returnsVersion1() {
        // Spring 이 application context 로드 시 VaultTransitPiiKeyVault 가 생성자에서 Vault Transit 를 호출해
        // dek-v1 과 hmac 을 복호화 후 in-memory 캐시한 상태이다.

        // then — 주입된 PiiKeyVault 가 VaultTransitPiiKeyVault 인스턴스여야 한다.
        assertThat(piiKeyVault).isInstanceOf(VaultTransitPiiKeyVault.class);

        // 활성 DEK 버전이 1 로 노출된다.
        PiiKeyVault.ActiveKey activeKey = piiKeyVault.getActiveDataEncryptionKey();
        assertThat(activeKey.version()).isEqualTo(1);
        assertThat(activeKey.key()).isNotNull();
        assertThat(activeKey.key().getAlgorithm()).isEqualTo("AES");
        assertThat(activeKey.key().getEncoded()).hasSize(32); // AES-256 = 32 bytes.
    }

    // ──────────────────────────────────────────────
    // Test 2: 명시적 버전 1 조회 시 동일한 DEK 반환
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getDataEncryptionKey_version1_returnsKey")
    void getDataEncryptionKey_version1_returnsKey() {
        // when — 명시적으로 버전 1 을 요청한다.
        SecretKey dek = piiKeyVault.getDataEncryptionKey(1);

        // then — 활성 DEK 와 동일한 키여야 한다.
        assertThat(dek).isNotNull();
        assertThat(dek.getAlgorithm()).isEqualTo("AES");
        assertThat(dek.getEncoded()).hasSize(32);
        // getActive() 결과와 일치하는지 확인 (캐시 일관성).
        assertThat(dek.getEncoded()).isEqualTo(piiKeyVault.getActiveDataEncryptionKey().key().getEncoded());
    }

    // ──────────────────────────────────────────────
    // Test 3: HMAC 키가 HmacSHA256 알고리즘으로 노출됨
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getHmacKey_returnsHmacSha256Key")
    void getHmacKey_returnsHmacSha256Key() {
        // when
        SecretKey hmacKey = piiKeyVault.getHmacKey();

        // then — HMAC 키는 non-null 이고 알고리즘은 HmacSHA256 이며 32 bytes (SHA-256 권장 길이).
        assertThat(hmacKey).isNotNull();
        assertThat(hmacKey.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(hmacKey.getEncoded()).hasSize(32);

        // 미등록 버전 조회는 여전히 PiiKeyVaultException 으로 실패해야 한다 (sanity check).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> piiKeyVault.getDataEncryptionKey(99))
                .isInstanceOf(PiiKeyVaultException.class);
    }
}
