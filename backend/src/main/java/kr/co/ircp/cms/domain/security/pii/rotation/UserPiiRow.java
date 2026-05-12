package kr.co.ircp.cms.domain.security.pii.rotation;

/**
 * 키 회전 대상 사용자 PII 행 (MyBatis 매핑 결과).
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 — {@code users} 테이블의 4개 PII 컬럼을
 * id 와 함께 단일 객체로 캡슐화한다.
 *
 * <p>주의: HMAC(email_hmac)은 데이터 암호화 키와 분리된 별도 키로 산출되므로
 * DEK(데이터 암호화 키) 회전 시에도 변경하지 않는다 — 따라서 본 record 에 포함하지 않는다.
 */
public record UserPiiRow(
        long id,
        byte[] emailEncrypted,
        byte[] emailIv,
        byte[] emailTag,
        int emailKeyVersion
) {
}
