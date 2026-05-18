package kr.co.ircp.cms.domain.ai.job;

import kr.co.ircp.cms.domain.ai.service.AiModelMetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 모델 성능 지표 일배치 잡.
 *
 * <p>SPEC-CMS-AI-001 — 매일 02:15에 어제 일자의 예측유형별 지표를 집계한다.
 * 멱등성: 동일 일자 재실행 시 metric UNIQUE(modelName,predictionType,
 * aggregatePeriod,periodStart) 제약 + insertOrUpdate(upsert)로 중복 적재되지 않는다.
 */
// @MX:NOTE: [AUTO] AiModelMetricJob — 02:15 cron, 어제 일자 고정 집계로 멱등 (UNIQUE 제약 의존)
// @MX:SPEC: SPEC-CMS-AI-001
@Component
public class AiModelMetricJob {

    private static final Logger log = LoggerFactory.getLogger(AiModelMetricJob.class);
    private static final List<String> PREDICTION_TYPES =
            List.of("GROWTH_STAGE", "RISK_SCORE", "SIMULATION");

    private final AiModelMetricService metricService;

    public AiModelMetricJob(AiModelMetricService metricService) {
        this.metricService = metricService;
    }

    /**
     * 매일 02:15 — 예측유형 3종에 대해 어제 일자 지표 집계.
     */
    // @MX:NOTE: [AUTO] @Scheduled cron "0 15 2 * * *" — 매일 02:15 실행, UNIQUE 제약으로 멱등성 보장
    // @MX:ANCHOR: [AUTO] 드리프트 감지 임계값: accuracy<0.70 || nRMSE>0.20 → ai_retrain_queue 자동 적재
    // @MX:REASON: 임계값 변경 시 재학습 트리거 빈도가 직접 변동 — 운영 정책 불변 계약
    // @MX:SPEC: SPEC-CMS-AI-001
    @Scheduled(cron = "0 15 2 * * *")
    public void aggregateMetrics() {
        LocalDate target = LocalDate.now().minusDays(1);
        for (String type : PREDICTION_TYPES) {
            try {
                metricService.aggregate(type, target);
            } catch (Exception e) {
                // 한 유형 실패가 나머지 집계를 막지 않도록 흡수
                log.error("AI 지표 집계 실패: type={}, date={}", type, target, e);
            }
        }
        log.info("AI 모델 지표 일배치 완료: date={}", target);
    }
}
