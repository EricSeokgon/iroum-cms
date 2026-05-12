package kr.co.ircp.cms.domain.security.pii.rotation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * PII 키 회전 배치 설정 프로퍼티.
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-001/002.
 *
 * <p>설정 키:
 * <ul>
 *   <li>{@code pii.rotation.cron-expression} — Spring cron (기본: 6개월마다 1일 새벽 2시)</li>
 *   <li>{@code pii.rotation.batch-size} — 청크 크기 (기본 1000 rows)</li>
 * </ul>
 *
 * <p>운영 결정 (D1/D2):
 * <ul>
 *   <li>D1: 6개월 주기 회전 — {@code 0 0 2 1 * /6 *} (6개월마다 1일 02:00)</li>
 *   <li>D2: 점진 배치 재암호화 — batch-size 단위 청크 처리, 무중단 운영</li>
 * </ul>
 */
@ConfigurationProperties("pii.rotation")
public record PiiKeyRotationProperties(
        @DefaultValue("0 0 2 1 */6 *") String cronExpression,
        @DefaultValue("1000") int batchSize
) {
}
