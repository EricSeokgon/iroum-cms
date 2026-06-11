package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.SimulationResultDto;
import kr.co.ircp.cms.domain.ai.dto.SimulationStartDto;
import kr.co.ircp.cms.domain.ai.mapper.AiSimulationSessionMapper;
import kr.co.ircp.cms.domain.ai.model.AiSimulationSession;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SimulationService 확장 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-SIM-001 — 비회원 창업기업 가상 시뮬레이션 환경 확장.
 * AI-001 위에 직원수(employeeCount)·투영기간(horizonYears: 3/5)·추천정책(recommendedPolicies) 추가.
 * 평문 IP 미저장 불변식(SHA-256 hash만)은 그대로 유지된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationService 확장 — 직원수·투영기간·추천정책 (SPEC-CMS-SIM-001)")
class SimulationServiceImplExtendTest {

    @Mock
    private AiSimulationSessionMapper sessionMapper;

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private AiPredictionLogService aiPredictionLogService;

    private SimulationService service;

    private static final String IP_HASH = "a".repeat(64); // SHA-256 hex 64자 — 평문 IP 아님

    @BeforeEach
    void setUp() {
        service = new SimulationServiceImpl(sessionMapper, mlServiceClient,
                aiPredictionLogService, 30);
    }

    private SimulationResponse mlResponse() {
        return new SimulationResponse(
                List.of(new SimulationResponse.ProjectionPoint(
                        2021, "STARTUP", Map.of("STARTUP", 0.7))),
                "sim-1.0.0");
    }

    /** insert 시 매퍼가 id를 채우는 운영 동작을 시뮬레이트한다. */
    private void stubInsertAssignsId() {
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, AiSimulationSession.class).setId(UUID.randomUUID());
            return null;
        }).when(sessionMapper).insert(any(AiSimulationSession.class));
    }

    private AiSimulationSession capturedSession() {
        ArgumentCaptor<AiSimulationSession> captor =
                ArgumentCaptor.forClass(AiSimulationSession.class);
        verify(sessionMapper, times(1)).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("start — horizonYears 미지정(3 기본) 세션에 저장")
    void start_with3YearHorizon_storesHorizonYears() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(0L);
        when(mlServiceClient.predictSimulation(any(SimulationRequest.class))).thenReturn(mlResponse());
        stubInsertAssignsId();

        // employeeCount=null, horizonYears=3
        SimulationStartDto dto = new SimulationStartDto(
                "J62010", 100_000_000L, 2020, null, null, 3);
        service.start(dto, IP_HASH);

        AiSimulationSession saved = capturedSession();
        assertThat(saved.getHorizonYears()).isEqualTo(3);
    }

    @Test
    @DisplayName("start — horizonYears=5 세션에 저장")
    void start_with5YearHorizon_storesHorizonYears() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(0L);
        when(mlServiceClient.predictSimulation(any(SimulationRequest.class))).thenReturn(mlResponse());
        stubInsertAssignsId();

        SimulationStartDto dto = new SimulationStartDto(
                "J62010", 100_000_000L, 2020, null, null, 5);
        service.start(dto, IP_HASH);

        AiSimulationSession saved = capturedSession();
        assertThat(saved.getHorizonYears()).isEqualTo(5);
    }

    @Test
    @DisplayName("start — employeeCount 세션에 저장")
    void start_withEmployeeCount_storesEmployeeCount() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(0L);
        when(mlServiceClient.predictSimulation(any(SimulationRequest.class))).thenReturn(mlResponse());
        stubInsertAssignsId();

        SimulationStartDto dto = new SimulationStartDto(
                "J62010", 100_000_000L, 2020, null, 12, 3);
        service.start(dto, IP_HASH);

        AiSimulationSession saved = capturedSession();
        assertThat(saved.getEmployeeCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("start — 결과 DTO에 horizonApplied 포함")
    void start_resultDto_includesHorizonApplied() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(0L);
        when(mlServiceClient.predictSimulation(any(SimulationRequest.class))).thenReturn(mlResponse());
        stubInsertAssignsId();

        SimulationStartDto dto = new SimulationStartDto(
                "J62010", 100_000_000L, 2020, null, null, 5);
        SimulationResultDto result = service.start(dto, IP_HASH);

        assertThat(result.horizonApplied()).isEqualTo(5);
    }

    @Test
    @DisplayName("ipHash 불변식 — 확장 후에도 평문 IP가 아닌 SHA-256 해시만 저장")
    void ipHash_neverStoresPlainIp() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(0L);
        when(mlServiceClient.predictSimulation(any(SimulationRequest.class))).thenReturn(mlResponse());
        stubInsertAssignsId();

        SimulationStartDto dto = new SimulationStartDto(
                "J62010", 100_000_000L, 2020, null, 5, 5);
        service.start(dto, IP_HASH);

        AiSimulationSession saved = capturedSession();
        assertThat(saved.getClientIpHash()).isEqualTo(IP_HASH);
        assertThat(saved.getClientIpHash()).hasSize(64);
        assertThat(saved.getClientIpHash()).doesNotContain(".").doesNotContain(":");
    }
}
