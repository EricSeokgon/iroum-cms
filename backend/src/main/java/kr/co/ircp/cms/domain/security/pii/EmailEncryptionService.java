package kr.co.ircp.cms.domain.security.pii;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Email AES-256-GCM 암호화 + HMAC 격상 서비스.
 *
 * <p>SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-001/002/003.
 *
 * <p>책임:
 * <ol>
 *   <li>평문 email → AES-256-GCM 암호화 (12-byte IV + 16-byte tag)</li>
 *   <li>암호화된 4 컬럼 → 평문 email 복호화 (무결성 검증)</li>
 *   <li>평문 email → HMAC-SHA256 격상 (lookup용, deterministic)</li>
 * </ol>
 *
 * <p>보안 결정:
 * <ul>
 *   <li>매 encrypt 호출마다 IV를 새로 생성 (RISK-PII-05 — IV 재사용 시 GCM 보안 완전 붕괴)</li>
 *   <li>SecureRandom 단일 인스턴스 공유 (thread-safe)</li>
 *   <li>tag mismatch는 PiiKeyVaultException으로 변환하여 호출 측에서 일관 처리</li>
 *   <li>HMAC normalize: trim + lowercase (이메일 대소문자/공백 무관 lookup)</li>
 * </ul>
 *
 * @MX:ANCHOR [AUTO] EmailEncryptionService — TypeHandler/AuthService에서 fan_in >= 3 예상
 * @MX:REASON 모든 email 평문↔암호 변환의 단일 진입점
 * @MX:SPEC SPEC-CMS-SECURITY-PII-001#REQ-PII-EMAIL-001/002/003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailEncryptionService {

    /** AES-256-GCM 알고리즘. */
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    /** HMAC 알고리즘. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** GCM IV 길이 (NIST 권장 96 bits = 12 bytes). */
    private static final int GCM_IV_LENGTH = 12;

    /** GCM authentication tag 길이 (128 bits = 16 bytes). */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /** GCM authentication tag 길이 (bytes). */
    private static final int GCM_TAG_LENGTH_BYTES = 16;

    /** SecureRandom 단일 인스턴스 (thread-safe). */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PiiKeyVault keyVault;

    /**
     * 평문 email을 AES-256-GCM으로 암호화한다 (REQ-PII-EMAIL-001).
     *
     * <p>매 호출마다 12-byte IV를 새로 생성하므로, 동일 plaintext여도 결과는 매번 다르다.
     *
     * @param plaintext 평문 email (null/빈 문자열 불가)
     * @return EncryptedEmail (ciphertext + iv + tag + keyVersion)
     * @throws IllegalArgumentException plaintext가 null 또는 빈 문자열인 경우
     * @throws PiiKeyVaultException 키 조회/암호화 실패 시
     */
    public EncryptedEmail encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("plaintext email은 비어있을 수 없습니다");
        }

        // 1) 활성 키 조회
        PiiKeyVault.ActiveKey active = keyVault.getActiveDataEncryptionKey();

        // 2) 12-byte IV 생성 — 절대 재사용 금지 (RISK-PII-05)
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        try {
            // 3) Cipher 초기화 (GCM 모드, IV + tag length 지정)
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, active.key(), spec);

            // 4) 암호화 (결과: ciphertext + tag 결합)
            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 5) ciphertext와 tag 분리 (마지막 16 bytes가 tag)
            int tagStart = cipherTextWithTag.length - GCM_TAG_LENGTH_BYTES;
            byte[] ciphertext = Arrays.copyOfRange(cipherTextWithTag, 0, tagStart);
            byte[] tag = Arrays.copyOfRange(cipherTextWithTag, tagStart, cipherTextWithTag.length);

            return new EncryptedEmail(ciphertext, iv, tag, active.version());
        } catch (Exception e) {
            throw new PiiKeyVaultException("Email 암호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 암호화된 email을 복호화한다 (REQ-PII-EMAIL-002).
     *
     * <p>tag mismatch 발생 시 GCM이 AEADBadTagException을 던지며,
     * 본 서비스는 이를 PiiKeyVaultException으로 변환한다 (RISK-PII-04 — 변조/키 불일치 감지).
     *
     * @param encrypted 암호화된 email
     * @return 평문 email
     * @throws IllegalArgumentException encrypted가 null인 경우
     * @throws PiiKeyVaultException 키 조회 실패, 무결성 위반(tag mismatch) 시
     */
    public String decrypt(EncryptedEmail encrypted) {
        if (encrypted == null) {
            throw new IllegalArgumentException("encrypted는 null일 수 없습니다");
        }

        // 1) 버전에 해당하는 키 조회
        var key = keyVault.getDataEncryptionKey(encrypted.keyVersion());

        try {
            // 2) Cipher 초기화 (GCM 모드, 동일 IV + tag length)
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, encrypted.iv());
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            // 3) ciphertext + tag 결합 후 복호화
            byte[] cipherTextWithTag = new byte[encrypted.ciphertext().length + encrypted.tag().length];
            System.arraycopy(encrypted.ciphertext(), 0, cipherTextWithTag, 0, encrypted.ciphertext().length);
            System.arraycopy(encrypted.tag(), 0, cipherTextWithTag,
                    encrypted.ciphertext().length, encrypted.tag().length);

            byte[] plaintext = cipher.doFinal(cipherTextWithTag);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            // RISK-PII-04 — 무결성 위반: 변조 또는 키 불일치
            log.error("Email 복호화 무결성 위반 — keyVersion={}", encrypted.keyVersion());
            throw new PiiKeyVaultException("Email 복호화 무결성 위반: tag mismatch", e);
        } catch (Exception e) {
            throw new PiiKeyVaultException("Email 복호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 평문 email을 HMAC-SHA256으로 격상한다 (REQ-PII-EMAIL-003 — lookup용).
     *
     * <p>정규화: trim + lowercase. 동일 정규화 결과는 항상 동일 HMAC 결과 (deterministic)이며,
     * 이를 통해 DB의 email_hmac 컬럼과 매칭한다.
     *
     * <p>HMAC 키는 데이터 암호화 키와 분리된 별도 키(REQ-PII-EMAIL-003 §3) — 동일 키 재사용 금지.
     *
     * @param plaintext 평문 email
     * @return HMAC-SHA256 hex 문자열 (소문자, 64 chars)
     * @throws IllegalArgumentException plaintext가 null인 경우
     * @throws PiiKeyVaultException HMAC 키 조회/계산 실패 시
     */
    public String computeHmac(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext는 null일 수 없습니다");
        }

        // 정규화: trim + lowercase (대소문자/공백 무관 lookup)
        String normalized = plaintext.trim().toLowerCase(Locale.ROOT);

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keyVault.getHmacKey());
            byte[] hmacBytes = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new PiiKeyVaultException("Email HMAC 계산 실패: " + e.getMessage(), e);
        }
    }
}
