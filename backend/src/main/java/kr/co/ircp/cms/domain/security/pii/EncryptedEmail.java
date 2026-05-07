package kr.co.ircp.cms.domain.security.pii;

/**
 * 암호화된 email 정보 (AES-256-GCM).
 *
 * <p>SPEC-CMS-SECURITY-PII-001 §4.1 — V24 4 column 분리 저장 매핑:
 * <ul>
 *   <li>email_encrypted: ciphertext (AES-256-GCM)</li>
 *   <li>email_iv: 12-byte IV (NIST 권장 96 bits)</li>
 *   <li>email_tag: 16-byte authentication tag (128 bits)</li>
 *   <li>email_key_version: 키 버전 (rotation 추적용, 1 이상)</li>
 * </ul>
 *
 * <p>Compact constructor에서 각 component의 형상(길이/범위)을 검증하여
 * 잘못된 데이터가 DB까지 흘러가는 것을 차단한다.
 *
 * @MX:NOTE [AUTO] EncryptedEmail은 4개 컬럼 묶음의 단일 값 객체
 * @MX:SPEC SPEC-CMS-SECURITY-PII-001#REQ-PII-EMAIL-001
 */
public record EncryptedEmail(
        byte[] ciphertext,
        byte[] iv,
        byte[] tag,
        int keyVersion
) {
    public EncryptedEmail {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("ciphertext는 비어있을 수 없습니다");
        }
        if (iv == null || iv.length != 12) {
            throw new IllegalArgumentException("iv는 12 bytes여야 합니다 (실제: "
                    + (iv == null ? "null" : iv.length) + ")");
        }
        if (tag == null || tag.length != 16) {
            throw new IllegalArgumentException("tag는 16 bytes여야 합니다 (실제: "
                    + (tag == null ? "null" : tag.length) + ")");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion은 1 이상이어야 합니다 (실제: " + keyVersion + ")");
        }
    }
}
