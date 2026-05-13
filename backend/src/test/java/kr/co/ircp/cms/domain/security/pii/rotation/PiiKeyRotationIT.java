package kr.co.ircp.cms.domain.security.pii.rotation;

import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PiiKeyRotationService PostgreSQL 통합 테스트.
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 — 실제 DB row 재암호화 통합 검증.
 *
 * <p>검증 시나리오:
 * <ol>
 *   <li>v1 키로 암호화된 users row 3건 삽입</li>
 *   <li>활성 키를 v2로 전환 후 {@link PiiKeyRotationService#rotatePendingAll()} 실행</li>
 *   <li>DB에서 email_key_version = 2 로 갱신됐는지 확인</li>
 *   <li>재암호화된 데이터를 복호화해 평문이 원본과 일치하는지 확인</li>
 *   <li>pii_key_rotation_log 에 COMPLETED 로그가 기록됐는지 확인</li>
 *   <li>email_hmac 이 회전 전후로 불변인지 확인</li>
 * </ol>
 */
// @MX:NOTE: [AUTO] PiiKeyRotationIT — AbstractIntegrationTest 상속 (PostgreSQL Singleton Container)
// @MX:SPEC: SPEC-CMS-SECURITY-PII-ROTATION-001#AC-ROT-001-1,002-1,003-1
@DisplayName("PiiKeyRotationIT — PostgreSQL 실제 재암호화 통합 테스트")
class PiiKeyRotationIT extends AbstractIntegrationTest {

    // v1/v2 테스트 키: 각 32 bytes (AES-256)
    private static final byte[] V1_KEY_BYTES = new byte[32]; // 0x00 × 32
    private static final byte[] V2_KEY_BYTES = new byte[32]; // 0xFF × 32

    static {
        Arrays.fill(V2_KEY_BYTES, (byte) 0xFF);
    }

    /**
     * 테스트 전용 PiiKeyVault — 활성 버전을 런타임에 변경할 수 있다.
     *
     * <p>@Primary 로 등록해 LocalEnvPiiKeyVault 대신 주입되도록 한다.
     */
    static class ControlledPiiKeyVault implements PiiKeyVault {

        private final SecretKey v1 = new SecretKeySpec(V1_KEY_BYTES, "AES");
        private final SecretKey v2 = new SecretKeySpec(V2_KEY_BYTES, "AES");
        private final SecretKey hmac = new SecretKeySpec(V1_KEY_BYTES, "HmacSHA256");

        private final AtomicInteger activeVersion = new AtomicInteger(1);

        void setActiveVersion(int v) {
            activeVersion.set(v);
        }

        @Override
        public ActiveKey getActiveDataEncryptionKey() {
            int ver = activeVersion.get();
            return new ActiveKey(ver, getDataEncryptionKey(ver));
        }

        @Override
        public SecretKey getDataEncryptionKey(int version) {
            return switch (version) {
                case 1 -> v1;
                case 2 -> v2;
                default -> throw new PiiKeyVaultException("알 수 없는 키 버전: " + version);
            };
        }

        @Override
        public SecretKey getHmacKey() {
            return hmac;
        }
    }

    @TestConfiguration
    static class RotationItConfig {
        @Bean
        @Primary
        ControlledPiiKeyVault controlledPiiKeyVault() {
            return new ControlledPiiKeyVault();
        }
    }

    @Autowired
    private ControlledPiiKeyVault controlledVault;

    @Autowired
    private PiiKeyRotationService rotationService;

    @Autowired
    private EmailEncryptionService emailEncryptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 각 테스트 시작 시 활성 키를 v1 로 초기화
        controlledVault.setActiveVersion(1);
    }

    // ──────────────────────────────────────────────
    // Test 1: 3건 삽입 → 활성 키 v2 전환 → 전체 회전 → email_key_version = 2 확인
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenThreeV1Users_whenRotate_thenAllKeyVersionsBecome2")
    void givenThreeV1Users_whenRotate_thenAllKeyVersionsBecome2() throws Exception {
        // given — v1 키로 암호화된 사용자 3건 삽입
        String[] emails = {"rotation-it-1@test.com", "rotation-it-2@test.com", "rotation-it-3@test.com"};
        long[] userIds = new long[3];

        for (int i = 0; i < emails.length; i++) {
            EncryptedEmail enc = encryptWithV1(emails[i]);
            String hmac = emailEncryptionService.computeHmac(emails[i]);
            userIds[i] = insertTestUser("user-rot-" + UUID.randomUUID(), enc, hmac);
        }

        // when — 활성 키를 v2 로 전환 후 배치 실행
        controlledVault.setActiveVersion(2);
        int totalRotated = rotationService.rotatePendingAll();

        // then — 3건 이상 재암호화 (다른 IT 로부터 v1 row 가 남아있을 수 있으므로 >= 3)
        assertThat(totalRotated).isGreaterThanOrEqualTo(3);

        // then — 삽입한 3건의 email_key_version 이 모두 2 로 갱신됐는지 확인
        for (long uid : userIds) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT email_key_version, email_encrypted, email_iv, email_tag FROM users WHERE id = ?", uid);
            assertThat(((Number) row.get("email_key_version")).intValue())
                    .as("userId=%d 의 email_key_version 이 2 여야 합니다", uid)
                    .isEqualTo(2);
        }
    }

    // ──────────────────────────────────────────────
    // Test 2: 재암호화 후 복호화 round-trip 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenV1User_whenRotate_thenDecryptRoundtripSucceeds")
    void givenV1User_whenRotate_thenDecryptRoundtripSucceeds() throws Exception {
        // given
        String plainEmail = "roundtrip-rotation@test.com";
        EncryptedEmail v1Enc = encryptWithV1(plainEmail);
        String hmacHex = emailEncryptionService.computeHmac(plainEmail);
        long userId = insertTestUser("user-rt-" + UUID.randomUUID(), v1Enc, hmacHex);

        // when
        controlledVault.setActiveVersion(2);
        rotationService.rotatePendingAll();

        // then — v2 암호문으로 복호화 시 평문 복원 확인
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email_encrypted, email_iv, email_tag, email_key_version FROM users WHERE id = ?",
                userId);

        byte[] ct = (byte[]) row.get("email_encrypted");
        byte[] iv = (byte[]) row.get("email_iv");
        byte[] tag = (byte[]) row.get("email_tag");
        int ver = ((Number) row.get("email_key_version")).intValue();

        EncryptedEmail v2Enc = new EncryptedEmail(ct, iv, tag, ver);
        String decrypted = emailEncryptionService.decrypt(v2Enc);

        assertThat(decrypted).isEqualTo(plainEmail);
    }

    // ──────────────────────────────────────────────
    // Test 3: email_hmac 불변성 확인 (DEK 회전 시 HMAC 미변경)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("givenV1User_whenRotate_thenEmailHmacUnchanged")
    void givenV1User_whenRotate_thenEmailHmacUnchanged() throws Exception {
        // given
        String email = "hmac-invariant@test.com";
        EncryptedEmail enc = encryptWithV1(email);
        String originalHmac = emailEncryptionService.computeHmac(email);
        long userId = insertTestUser("user-hmac-" + UUID.randomUUID(), enc, originalHmac);

        // when
        controlledVault.setActiveVersion(2);
        rotationService.rotatePendingAll();

        // then — HMAC 컬럼은 DEK 회전과 무관하게 불변이어야 한다
        String hmacAfter = jdbcTemplate.queryForObject(
                "SELECT email_hmac FROM users WHERE id = ?", String.class, userId);
        assertThat(hmacAfter).isEqualTo(originalHmac);
    }

    // ──────────────────────────────────────────────
    // Test 4: pii_key_rotation_log COMPLETED 기록 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("whenRotate_thenRotationLogStatusCompleted")
    void whenRotate_thenRotationLogStatusCompleted() throws Exception {
        // given — 회전할 row 가 없어도 로그는 기록된다
        controlledVault.setActiveVersion(2);
        // 이미 v2 인 row 만 있거나 0건인 경우도 COMPLETED 로 기록
        rotationService.rotatePendingAll();

        // then — 가장 최근 rotation_log 가 COMPLETED 상태여야 한다
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM pii_key_rotation_log ORDER BY id DESC LIMIT 1",
                String.class);
        assertThat(status).isEqualTo("COMPLETED");
    }

    // ──────────────────────────────────────────────
    // 헬퍼: v1 키로 직접 AES-256-GCM 암호화
    // ──────────────────────────────────────────────

    /**
     * v1 키(0x00 × 32)로 plaintext 를 직접 암호화해 {@link EncryptedEmail} 을 반환한다.
     * Spring 컨텍스트의 활성 버전과 무관하게 v1 암호문 생성 시 사용한다.
     */
    private static EncryptedEmail encryptWithV1(String plaintext) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        SecretKey v1 = new SecretKeySpec(V1_KEY_BYTES, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, v1, new GCMParameterSpec(128, iv));
        byte[] withTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] ct = Arrays.copyOf(withTag, withTag.length - 16);
        byte[] tag = Arrays.copyOfRange(withTag, withTag.length - 16, withTag.length);
        return new EncryptedEmail(ct, iv, tag, 1); // keyVersion = 1
    }

    /**
     * 테스트용 사용자 row 를 users 테이블에 삽입하고 생성된 id 를 반환한다.
     */
    private long insertTestUser(String username, EncryptedEmail enc, String emailHmac) {
        // V26: email 평문 컬럼 DROP — INSERT 에서 email 제거
        jdbcTemplate.update(
                "INSERT INTO users " +
                "(username, password_hash, name, status, " +
                " email_encrypted, email_iv, email_tag, email_hmac, email_key_version, " +
                " password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', 'Rotation Test', 'ACTIVE', " +
                "        ?, ?, ?, ?, 1, NOW(), NOW(), NOW())",
                username,
                enc.ciphertext(), enc.iv(), enc.tag(), emailHmac
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
    }
}
