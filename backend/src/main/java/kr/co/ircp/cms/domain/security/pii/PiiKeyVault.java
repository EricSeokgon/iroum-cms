package kr.co.ircp.cms.domain.security.pii;

import javax.crypto.SecretKey;

/**
 * PII (개인정보) 암호화 키 관리 인터페이스.
 *
 * <p>SPEC-CMS-SECURITY-PII-001 REQ-PII-EMAIL-004: 키 관리 인터페이스
 *
 * <p>구현체:
 * - LocalEnvPiiKeyVault: 환경변수 기반 (개발 + 1차 운영)
 * - (후속) AwsKmsKeyVault: AWS KMS 통합
 * - (후속) HashiCorpVaultKeyVault: HashiCorp Vault 통합
 *
 * @MX:ANCHOR PiiKeyVault — TypeHandler/AuthService에서 fan_in >= 3 예상
 * @MX:REASON PII 암호화 키 관리 단일 진입점, 키 회전 인터페이스의 중심
 * @MX:SPEC SPEC-CMS-SECURITY-PII-001#REQ-PII-EMAIL-004
 */
public interface PiiKeyVault {

    /**
     * 현재 활성 데이터 암호화 키(DEK)와 버전을 반환한다.
     * 새로운 INSERT/UPDATE 시 사용된다.
     *
     * @return 활성 키 정보
     * @throws PiiKeyVaultException 키 조회 실패 시
     */
    ActiveKey getActiveDataEncryptionKey();

    /**
     * 지정된 버전의 데이터 암호화 키를 조회한다.
     * 기존 암호화 데이터 복호화 시 사용된다.
     *
     * @param version 키 버전 (>= 1)
     * @return 데이터 암호화 키
     * @throws PiiKeyVaultException 버전이 존재하지 않거나 조회 실패 시
     */
    SecretKey getDataEncryptionKey(int version);

    /**
     * HMAC 키를 반환한다 (lookup용).
     * 데이터 암호화 키와 분리된 별도 키.
     *
     * @return HMAC 키 (HMAC-SHA256)
     * @throws PiiKeyVaultException 키 조회 실패 시
     */
    SecretKey getHmacKey();

    /**
     * 활성 키 정보 (키 + 버전 번호).
     */
    record ActiveKey(int version, SecretKey key) {}
}
