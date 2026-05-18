package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.mapper.AiModelMetricMapper;
import kr.co.ircp.cms.domain.ai.mapper.AiPredictionLogMapper;
import kr.co.ircp.cms.domain.ai.mapper.AiRetrainQueueMapper;
import kr.co.ircp.cms.domain.ai.model.AiModelMetric;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.domain.ai.model.AiRetrainQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiModelMetricService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-001 Step 2 — 예측 로그 기반 RMSE/MAE/accuracy 집계 + 드리프트 탐지.
 * accuracy &lt; 0.70 또는 nRMSE &gt; 0.20 시 drift_detected=true + 재학습 큐 적재.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiModelMetricService — 집계·드리프트 (SPEC-CMS-AI-001)")
class AiModelMetricServiceTest {

    @Mock
    private AiPredictionLogMapper predictionLogMapper;

    @Mock
    private AiModelMetricMapper metricMapper;

    @Mock
    private AiRetrainQueueMapper retrainQueueMapper;

    private AiModelMetricService service;

    @BeforeEach
    void setUp() {
        service = new AiModelMetricServiceImpl(predictionLogMapper, metricMapper,
                retrainQueueMapper);
    }

    /** predicted=2.0, actual=2.0 → 오차 0 (높은 정확도, 드리프트 없음). */
    private AiPredictionLog accurateLog() {
        return AiPredictionLog.builder()
                .id(1L)
                .predictionType("RISK_SCORE")
                .modelName("risk-model")
                .latencyMs(120)
                .status("SUCCESS")
                .outputResult("{\"defaultProbability\":0.20}")
                .actualValue("{\"defaultProbability\":0.20}")
                .build();
    }

    /** predicted=0.20, actual=0.95 → 큰 오차 (낮은 정확도 → 드리프트). */
    private AiPredictionLog inaccurateLog() {
        return AiPredictionLog.builder()
                .id(2L)
                .predictionType("RISK_SCORE")
                .modelName("risk-model")
                .latencyMs(140)
                .status("SUCCESS")
                .outputResult("{\"defaultProbability\":0.20}")
                .actualValue("{\"defaultProbability\":0.95}")
                .build();
    }

    @Test
    @DisplayName("aggregate — RMSE/MAE/accuracy 산출 후 metric upsert")
    void aggregate_computesMetrics() {
        when(predictionLogMapper.findByPredictionType(anyString(), anyInt()))
                .thenReturn(List.of(accurateLog(), accurateLog()));

        service.aggregate("RISK_SCORE", LocalDate.of(2026, 5, 17));

        ArgumentCaptor<AiModelMetric> captor = ArgumentCaptor.forClass(AiModelMetric.class);
        verify(metricMapper, times(1)).insertOrUpdate(captor.capture());
        AiModelMetric metric = captor.getValue();
        assertThat(metric.getPredictionType()).isEqualTo("RISK_SCORE");
        assertThat(metric.getRmse()).isNotNull();
        assertThat(metric.getMae()).isNotNull();
        assertThat(metric.getAccuracy()).isNotNull();
        assertThat(metric.getSampleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("aggregate — accuracy < 0.70 시 drift_detected=true + 재학습 큐(DRIFT_ACCURACY) 적재")
    void aggregate_driftByLowAccuracy() {
        when(predictionLogMapper.findByPredictionType(anyString(), anyInt()))
                .thenReturn(List.of(inaccurateLog(), inaccurateLog(), inaccurateLog()));

        service.aggregate("RISK_SCORE", LocalDate.of(2026, 5, 17));

        ArgumentCaptor<AiModelMetric> metricCaptor = ArgumentCaptor.forClass(AiModelMetric.class);
        verify(metricMapper).insertOrUpdate(metricCaptor.capture());
        assertThat(metricCaptor.getValue().isDriftDetected()).isTrue();

        ArgumentCaptor<AiRetrainQueue> queueCaptor = ArgumentCaptor.forClass(AiRetrainQueue.class);
        verify(retrainQueueMapper, times(1)).insert(queueCaptor.capture());
        AiRetrainQueue queued = queueCaptor.getValue();
        assertThat(queued.getTriggerReason()).isIn("DRIFT_ACCURACY", "DRIFT_ERROR");
        assertThat(queued.getStatus()).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("aggregate — 임계 미달(정확도 양호) 시 드리프트 없음 + 재학습 큐 미적재")
    void aggregate_noDrift() {
        when(predictionLogMapper.findByPredictionType(anyString(), anyInt()))
                .thenReturn(List.of(accurateLog(), accurateLog(), accurateLog()));

        service.aggregate("RISK_SCORE", LocalDate.of(2026, 5, 17));

        ArgumentCaptor<AiModelMetric> metricCaptor = ArgumentCaptor.forClass(AiModelMetric.class);
        verify(metricMapper).insertOrUpdate(metricCaptor.capture());
        assertThat(metricCaptor.getValue().isDriftDetected()).isFalse();
        verify(retrainQueueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("aggregate — 라벨링된 로그가 없으면 메트릭 미적재 (빈 집계 스킵)")
    void aggregate_emptyLogs_skips() {
        when(predictionLogMapper.findByPredictionType(anyString(), anyInt()))
                .thenReturn(List.of());

        service.aggregate("RISK_SCORE", LocalDate.of(2026, 5, 17));

        verify(metricMapper, never()).insertOrUpdate(any());
        verify(retrainQueueMapper, never()).insert(any());
    }
}
