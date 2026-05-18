package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.job.AiModelMetricJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AiModelMetricJob 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-001 Step 2 — 일배치 집계 잡.
 * 예측유형별(GROWTH_STAGE/RISK_SCORE/SIMULATION) 집계 위임 + 멱등성.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiModelMetricJob — 일배치 집계 (SPEC-CMS-AI-001)")
class AiModelMetricJobTest {

    @Mock
    private AiModelMetricService metricService;

    private AiModelMetricJob job;

    @BeforeEach
    void setUp() {
        job = new AiModelMetricJob(metricService);
    }

    @Test
    @DisplayName("aggregateMetrics — 예측유형 3종 각각에 대해 집계 위임")
    void aggregateMetrics_perPredictionType() {
        job.aggregateMetrics();

        verify(metricService, times(1))
                .aggregate(eq("GROWTH_STAGE"), any(LocalDate.class));
        verify(metricService, times(1))
                .aggregate(eq("RISK_SCORE"), any(LocalDate.class));
        verify(metricService, times(1))
                .aggregate(eq("SIMULATION"), any(LocalDate.class));
    }

    @Test
    @DisplayName("aggregateMetrics — 같은 날 두 번 실행해도 동일 일자(어제)로 집계 위임 (멱등 — UNIQUE 제약 의존)")
    void aggregateMetrics_idempotentSameDay() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        job.aggregateMetrics();
        job.aggregateMetrics();

        // 두 번 실행 시 동일 일자로 6회 위임 — 멱등성은 metric UNIQUE 제약 + insertOrUpdate가 보장
        verify(metricService, times(2)).aggregate(eq("RISK_SCORE"), eq(yesterday));
    }
}
