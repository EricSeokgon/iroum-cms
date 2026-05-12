package kr.co.ircp.cms.domain.security.pii.rotation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PII 키 회전 cron 트리거 (D4: Cron 자동 배치).
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-003.
 *
 * <p>운영 결정 (D1):
 * <ul>
 *   <li>기본 cron: {@code 0 0 2 1 * /6 *} — 6개월마다 1일 새벽 2시 (Asia/Seoul)</li>
 *   <li>{@code pii.rotation.cron-expression} 프로퍼티로 override 가능</li>
 * </ul>
 *
 * <p>스케줄러 활성화: {@link org.springframework.scheduling.annotation.EnableScheduling}
 * 가 {@code AsyncConfig} 에서 이미 활성화되어 있으므로 본 클래스는 별도 활성화하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(PiiKeyRotationProperties.class)
public class PiiKeyRotationJob {

    private final PiiKeyRotationService rotationService;

    /**
     * Cron 트리거 — 회전 배치를 1회 실행한다.
     *
     * <p>예외는 로그만 남기고 swallow 한다 — 다음 회전 주기에 재시도하도록 한다.
     * 단일 회전 실패가 스케줄러 자체를 중단시키면 안 되기 때문이다.
     */
    @Scheduled(cron = "${pii.rotation.cron-expression:0 0 2 1 */6 *}", zone = "Asia/Seoul")
    public void runKeyRotation() {
        log.info("PII 키 회전 cron 트리거 시작");
        try {
            int total = rotationService.rotatePendingAll();
            log.info("PII 키 회전 cron 트리거 종료 — totalProcessed={}", total);
        } catch (RuntimeException e) {
            // pii_key_rotation_log 에 이미 FAILED 로 기록됨 → 여기서는 추가 로그만 남기고 다음 주기에 위임.
            log.error("PII 키 회전 cron 실행 중 예외 — 다음 주기에 재시도", e);
        }
    }
}
