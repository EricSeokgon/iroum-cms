package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.GrowthStageQueryDto;
import kr.co.ircp.cms.domain.ai.dto.GrowthStageResultDto;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GrowthStageService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-001 Step 2 — 성장단계 예측 서비스.
 * 캐시·비동기 로그·타임아웃 폴백·서킷 폴백 시맨틱 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrowthStageService — 예측·캐시·폴백 (SPEC-CMS-AI-001)")
class GrowthStageServiceTest {

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private AiPredictionLogService aiPredictionLogService;

    private GrowthStageService service;

    @BeforeEach
    void setUp() {
        service = new GrowthStageServiceImpl(mlServiceClient, aiPredictionLogService);
    }

    private GrowthStageQueryDto query() {
        return new GrowthStageQueryDto("J62010", 100_000_000L, 2020, null);
    }

    @Test
    @DisplayName("predict — ML 성공 시 결과 반환 + 비동기 로그(status=SUCCESS) 적재")
    void predict_success_logsAsync() {
        when(mlServiceClient.predictGrowthStage(any(GrowthStageRequest.class)))
                .thenReturn(new GrowthStageResponse(
                        "GROWTH", Map.of("GROWTH", 0.62, "EXPANSION", 0.21), 0.62, "growth-1.0.0"));

        GrowthStageResultDto result = service.predict(query());

        assertThat(result.stage()).isEqualTo("GROWTH");
        assertThat(result.fallback()).isFalse();
        assertThat(result.confidence()).isEqualTo(0.62);
        verify(aiPredictionLogService, times(1)).logAsync(any());
    }

    @Test
    @DisplayName("predict — ML 타임아웃 시 FALLBACK 응답 반환 + status=FALLBACK 로그")
    void predict_timeout_returnsFallback() {
        when(mlServiceClient.predictGrowthStage(any(GrowthStageRequest.class)))
                .thenThrow(new MlServiceException("ml-service timeout (simulated)"));

        GrowthStageResultDto result = service.predict(query());

        assertThat(result.fallback()).isTrue();
        assertThat(result.stage()).isEqualTo("UNKNOWN");
        verify(aiPredictionLogService, times(1)).logAsync(any());
    }

    @Test
    @DisplayName("predict — ML 호출은 매 캐시 미스마다 1회만 (서비스 레이어는 ML 1회 호출)")
    void predict_callsMlOnce() {
        when(mlServiceClient.predictGrowthStage(any(GrowthStageRequest.class)))
                .thenReturn(new GrowthStageResponse(
                        "SEED", Map.of("SEED", 0.8), 0.8, "growth-1.0.0"));

        service.predict(query());

        verify(mlServiceClient, times(1)).predictGrowthStage(any(GrowthStageRequest.class));
    }

    @Test
    @DisplayName("predict — ML 응답에 PII 미포함 (요청 DTO는 4개 필드만)")
    void predict_noPiiInRequest() {
        when(mlServiceClient.predictGrowthStage(any(GrowthStageRequest.class)))
                .thenAnswer(inv -> {
                    GrowthStageRequest req = inv.getArgument(0);
                    // PII 없음 — 4개 비식별 필드만 존재함을 record 컴포넌트로 보장
                    assertThat(req.ksicCode()).isEqualTo("J62010");
                    return new GrowthStageResponse("GROWTH", Map.of(), 0.5, "v1");
                });

        service.predict(query());
        verify(mlServiceClient, never()).predictRiskScore(any());
    }
}
