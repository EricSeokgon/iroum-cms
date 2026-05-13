package kr.co.ircp.cms.domain.security.pii.kms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

// @MX:NOTE: [AUTO] KMS 키 설정 — 암호화된 DEK/HMAC 키를 환경변수 또는 yml에서 주입
// @MX:SPEC: SPEC-CMS-SECURITY-PII-KMS-001
@ConfigurationProperties(prefix = "pii.keyvault.aws-kms")
public record AwsKmsPiiKeyVaultProperties(
        String keyId,                      // KMS CMK ARN or alias
        String region,                     // AWS region (e.g. ap-northeast-2)
        int activeVersion,                 // 활성 DEK 버전
        String endpointOverride,           // optional, for LocalStack (null in prod)
        Map<String, String> encryptedKeys  // key: "dek-v1", "dek-v2", "hmac" → value: base64 ciphertext
) {
}
