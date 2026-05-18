package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.config.RiskThresholdProperties;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreQueryDto;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreResultDto;
import kr.co.ircp.cms.domain.ai.exception.AiPredictionNotFoundException;
import kr.co.ircp.cms.domain.ai.mapper.AiPredictionLogMapper;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RiskScoreService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-001 Step 2 — 위험도 등급 임계 매핑 + 설명(topFactors) 조회.
 * p&lt;0.25 GREEN / 0.25≤p&lt;0.50 YELLOW / 0.50≤p&lt;0.75 ORANGE / p≥0.75 RED.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskScoreService — 등급 매핑·설명 (SPEC-CMS-AI-001)")
class RiskScoreServiceTest {

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private AiPredictionLogMapper predictionLogMapper;

    @Mock
    private AiPredictionLogService aiPredictionLogService;

    private RiskScoreService service;

    @BeforeEach
    void setUp() {
        RiskThresholdProperties thresholds = new RiskThresholdProperties();
        thresholds.setGreen(0.25);
        thresholds.setYellow(0.50);
        thresholds.setOrange(0.75);
        service = new RiskScoreServiceImpl(mlServiceClient, predictionLogMapper,
                aiPredictionLogService, thresholds);
    }

    private RiskScoreQueryDto query() {
        return new RiskScoreQueryDto("J62010", 100_000_000L, 2020, null);
    }

    private void stubMlProbability(double p) {
        when(mlServiceClient.predictRiskScore(any(RiskScoreRequest.class)))
                .thenReturn(new RiskScoreResponse(p, "IGNORED",
                        List.of(new RiskScoreResponse.RiskFactor("capitalAdequacy", 0.4)),
                        "risk-1.0.0"));
    }

    @Test
    @DisplayName("score — p<0.25 → GREEN")
    void score_green() {
        stubMlProbability(0.10);
        assertThat(service.score(query()).riskGrade()).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("score — 0.25≤p<0.50 → YELLOW (경계값 0.25 포함)")
    void score_yellow_boundary() {
        stubMlProbability(0.25);
        assertThat(service.score(query()).riskGrade()).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("score — 0.50≤p<0.75 → ORANGE (경계값 0.50 포함)")
    void score_orange_boundary() {
        stubMlProbability(0.50);
        assertThat(service.score(query()).riskGrade()).isEqualTo("ORANGE");
    }

    @Test
    @DisplayName("score — p≥0.75 → RED (경계값 0.75 포함)")
    void score_red_boundary() {
        stubMlProbability(0.75);
        assertThat(service.score(query()).riskGrade()).isEqualTo("RED");
    }

    @Test
    @DisplayName("explain — 예측 로그의 output_result에서 topFactors 반환")
    void explain_returnsTopFactors() {
        AiPredictionLog log = AiPredictionLog.builder()
                .id(42L)
                .predictionType("RISK_SCORE")
                .outputResult("{\"defaultProbability\":0.18,\"riskGrade\":\"GREEN\","
                        + "\"topFactors\":[{\"name\":\"capitalAdequacy\",\"contribution\":0.41}]}")
                .build();
        when(predictionLogMapper.findById(42L)).thenReturn(Optional.of(log));

        RiskScoreResultDto result = service.explain(42L);

        assertThat(result.topFactors()).isNotEmpty();
        assertThat(result.topFactors().get(0).name()).isEqualTo("capitalAdequacy");
    }

    @Test
    @DisplayName("explain — 미존재 예측 ID면 AiPredictionNotFoundException")
    void explain_notFound() {
        when(predictionLogMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.explain(999L))
                .isInstanceOf(AiPredictionNotFoundException.class);
    }
}
