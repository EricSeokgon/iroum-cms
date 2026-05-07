package kr.co.ircp.cms.domain.security.pii;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-SECURITY-PII-001 통합 테스트.
 *
 * <p>V24 마이그레이션 적용 후의 PII 암호화 흐름을 실제 PostgreSQL 16 환경에서 검증한다.
 *
 * <p>검증 시나리오:
 * <ul>
 *   <li>V24 컬럼이 존재하는 상태에서 사용자 INSERT 시 email_encrypted/iv/tag/hmac/key_version 적재</li>
 *   <li>email 평문 컬럼은 NULL 또는 평문, 둘 다 무방 (Step 5 마이그레이션 전까지)</li>
 *   <li>findByEmailHmac 으로 HMAC 매칭 lookup 가능</li>
 *   <li>저장된 암호화 데이터를 EmailEncryptionService 로 복호화하면 원본 일치</li>
 * </ul>
 */
@DisplayName("PII Email 통합 테스트 (SPEC-CMS-SECURITY-PII-001)")
@Transactional
class PiiEmailIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailEncryptionService emailEncryptionService;

    @Test
    @DisplayName("V24 적용 후 — 암호화된 email로 사용자 INSERT/SELECT 라운드트립")
    void encryptStoreDecrypt_roundTrip() {
        String plaintext = "pii.user." + UUID.randomUUID() + "@example.com";

        // 1) email 암호화 + HMAC 계산
        EncryptedEmail encrypted = emailEncryptionService.encrypt(plaintext);
        String hmac = emailEncryptionService.computeHmac(plaintext);

        // 2) User 엔티티 구성 (PII 컬럼 set)
        User user = User.builder()
                .username("pii_user_" + UUID.randomUUID().toString().substring(0, 8))
                .email(plaintext)                       // 평문 (V25 전까지는 NULL/plain 모두 허용)
                .passwordHash("$2a$12$dummyhashfortestonly00000000000000000000000000000000000000")
                .name("PII Test User")
                .status(UserStatus.ACTIVE)
                .emailEncrypted(encrypted.ciphertext())
                .emailIv(encrypted.iv())
                .emailTag(encrypted.tag())
                .emailKeyVersion(encrypted.keyVersion())
                .emailHmac(hmac)
                .build();

        userMapper.insert(user);
        assertThat(user.getId()).isNotNull();

        // 3) ID로 조회 → 암호화 컬럼 정확히 복원
        User loaded = userMapper.findById(user.getId()).orElseThrow();
        assertThat(loaded.getEmailEncrypted()).isEqualTo(encrypted.ciphertext());
        assertThat(loaded.getEmailIv()).isEqualTo(encrypted.iv());
        assertThat(loaded.getEmailTag()).isEqualTo(encrypted.tag());
        assertThat(loaded.getEmailKeyVersion()).isEqualTo(encrypted.keyVersion());
        assertThat(loaded.getEmailHmac()).isEqualTo(hmac);

        // 4) 복호화 검증 — 원본 일치
        EncryptedEmail reconstructed = new EncryptedEmail(
                loaded.getEmailEncrypted(),
                loaded.getEmailIv(),
                loaded.getEmailTag(),
                loaded.getEmailKeyVersion() != null ? loaded.getEmailKeyVersion() : 1
        );
        String decrypted = emailEncryptionService.decrypt(reconstructed);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("findByEmailHmac — HMAC 매칭으로 사용자 정확히 조회")
    void findByEmailHmac_matchesByHmac() {
        String plaintext = "lookup.user." + UUID.randomUUID() + "@example.com";

        EncryptedEmail encrypted = emailEncryptionService.encrypt(plaintext);
        String hmac = emailEncryptionService.computeHmac(plaintext);

        User user = User.builder()
                .username("lookup_user_" + UUID.randomUUID().toString().substring(0, 8))
                .email(plaintext)
                .passwordHash("$2a$12$dummyhashfortestonly00000000000000000000000000000000000000")
                .name("Lookup Test")
                .status(UserStatus.ACTIVE)
                .emailEncrypted(encrypted.ciphertext())
                .emailIv(encrypted.iv())
                .emailTag(encrypted.tag())
                .emailKeyVersion(encrypted.keyVersion())
                .emailHmac(hmac)
                .build();
        userMapper.insert(user);

        // HMAC 으로 정확히 lookup 가능
        Optional<User> found = userMapper.findByEmailHmac(hmac);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
        assertThat(found.get().getEmailHmac()).isEqualTo(hmac);
    }

    @Test
    @DisplayName("findByEmailHmac — 정규화 (대소문자/공백) 후 동일 HMAC → 동일 사용자 조회")
    void findByEmailHmac_normalizationConsistency() {
        String plaintext = "case.test." + UUID.randomUUID() + "@example.com";
        String upperWithSpaces = "  " + plaintext.toUpperCase() + "  ";

        EncryptedEmail encrypted = emailEncryptionService.encrypt(plaintext);
        String hmac = emailEncryptionService.computeHmac(plaintext);

        User user = User.builder()
                .username("case_user_" + UUID.randomUUID().toString().substring(0, 8))
                .email(plaintext)
                .passwordHash("$2a$12$dummyhashfortestonly00000000000000000000000000000000000000")
                .name("Case Test")
                .status(UserStatus.ACTIVE)
                .emailEncrypted(encrypted.ciphertext())
                .emailIv(encrypted.iv())
                .emailTag(encrypted.tag())
                .emailKeyVersion(encrypted.keyVersion())
                .emailHmac(hmac)
                .build();
        userMapper.insert(user);

        // 대소문자/공백 다른 입력 → 동일 정규화 결과 → 동일 HMAC → 동일 사용자
        String hmacOfVariant = emailEncryptionService.computeHmac(upperWithSpaces);
        assertThat(hmacOfVariant).isEqualTo(hmac);

        Optional<User> found = userMapper.findByEmailHmac(hmacOfVariant);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("findByEmailHmac — 미존재 HMAC → Optional.empty()")
    void findByEmailHmac_unknown_returnsEmpty() {
        String unknownHmac = "0".repeat(64);
        Optional<User> result = userMapper.findByEmailHmac(unknownHmac);
        assertThat(result).isEmpty();
    }
}
