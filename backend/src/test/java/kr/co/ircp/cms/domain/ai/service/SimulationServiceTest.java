package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.SimulationResultDto;
import kr.co.ircp.cms.domain.ai.dto.SimulationStartDto;
import kr.co.ircp.cms.domain.ai.exception.AiRateLimitExceededException;
import kr.co.ircp.cms.domain.ai.exception.AiSimulationNotFoundException;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SimulationService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-001 Step 2 — 익명 시뮬레이션 세션 생성·조회·레이트리밋·만료.
 * 평문 IP 미저장(SHA-256 hash만), 30 req/hour/ip-hash 제한 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationService — 세션 생성·레이트리밋·만료 (SPEC-CMS-AI-001)")
class SimulationServiceTest {

    @Mock
    private AiSimulationSessionMapper sessionMapper;

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private AiPredictionLogService aiPredictionLogService;

    private SimulationService service;

    private static final String IP_HASH =
            "a".repeat(64); // SHA-256 hex 64자 — 평문 IP 아님

    @BeforeEach
    void setUp() {
        // rate-limit 기본 30/hour 주입
        service = new SimulationServiceImpl(sessionMapper, mlServiceClient,
                aiPredictionLogService, 30);
    }

    private SimulationStartDto startDto() {
        // SIM-001 — employeeCount=null, horizonYears=null(기본 3 보정)
        return new SimulationStartDto("J62010", 100_000_000L, 2020, null, null, null);
    }

    private SimulationResponse mlResponse() {
        return new SimulationResponse(
                List.of(new SimulationResponse.ProjectionPoint(
                        2021, "STARTUP", Map.of("STARTUP", 0.7))),
                "sim-1.0.0");
    }

    @Test
    @DisplayName("start — UUID 세션 생성 + SHA-256 ip 해시만 저장 (평문 IP 금지)")
    void start_storesIpHashNotPlaintext() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(0L);
        when(mlServiceClient.predictSimulation(any(SimulationRequest.class)))
                .thenReturn(mlResponse());
        // 운영 매퍼는 gen_random_uuid() DEFAULT + useGeneratedKeys로 id를 채운다 — 동일하게 시뮬레이트
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, AiSimulationSession.class).setId(UUID.randomUUID());
            return null;
        }).when(sessionMapper).insert(any(AiSimulationSession.class));

        SimulationResultDto result = service.start(startDto(), IP_HASH);

        ArgumentCaptor<AiSimulationSession> captor =
                ArgumentCaptor.forClass(AiSimulationSession.class);
        verify(sessionMapper, times(1)).insert(captor.capture());
        AiSimulationSession saved = captor.getValue();
        assertThat(saved.getClientIpHash()).isEqualTo(IP_HASH);
        assertThat(saved.getClientIpHash()).hasSize(64);
        // 평문 IP(점/콜론 표기)가 절대 들어가지 않음
        assertThat(saved.getClientIpHash()).doesNotContain(".").doesNotContain(":");
        assertThat(result.sessionId()).isNotNull();
    }

    @Test
    @DisplayName("start — 1시간 내 30회 초과 시 AiRateLimitExceededException")
    void start_rateLimitExceeded() {
        when(sessionMapper.countByIpHashSince(eq(IP_HASH), any(Instant.class))).thenReturn(30L);

        assertThatThrownBy(() -> service.start(startDto(), IP_HASH))
                .isInstanceOf(AiRateLimitExceededException.class);

        verify(sessionMapper, times(0)).insert(any());
    }

    @Test
    @DisplayName("getResult — 존재하고 미만료 세션이면 projection 반환")
    void getResult_returnsProjection() {
        UUID id = UUID.randomUUID();
        AiSimulationSession session = AiSimulationSession.builder()
                .id(id)
                .ksicCode("J62010")
                .pdfStatus("NONE")
                .clientIpHash(IP_HASH)
                .projectionResult("{\"projection\":[]}")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        when(sessionMapper.findById(id)).thenReturn(Optional.of(session));

        SimulationResultDto result = service.getResult(id);

        assertThat(result.sessionId()).isEqualTo(id);
        assertThat(result.projectionResult()).isEqualTo("{\"projection\":[]}");
    }

    @Test
    @DisplayName("getResult — 미존재 세션이면 AiSimulationNotFoundException")
    void getResult_notFound() {
        UUID id = UUID.randomUUID();
        when(sessionMapper.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(id))
                .isInstanceOf(AiSimulationNotFoundException.class);
    }

    @Test
    @DisplayName("getResult — 만료(expiresAt < now) 세션이면 AiSimulationNotFoundException")
    void getResult_expired() {
        UUID id = UUID.randomUUID();
        AiSimulationSession expired = AiSimulationSession.builder()
                .id(id)
                .pdfStatus("NONE")
                .clientIpHash(IP_HASH)
                .createdAt(Instant.now().minus(25, ChronoUnit.HOURS))
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(sessionMapper.findById(id)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getResult(id))
                .isInstanceOf(AiSimulationNotFoundException.class);
    }

    @Test
    @DisplayName("generatePdf — pdf_status NONE → GENERATING → READY 전이 + PDF 바이트 반환")
    void generatePdf_statusTransition() {
        UUID id = UUID.randomUUID();
        AiSimulationSession session = AiSimulationSession.builder()
                .id(id)
                .ksicCode("J62010")
                .capitalAmount(100_000_000L)
                .foundingYear(2020)
                .pdfStatus("NONE")
                .clientIpHash(IP_HASH)
                .projectionResult("{\"projection\":[]}")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        when(sessionMapper.findById(id)).thenReturn(Optional.of(session));
        lenient().when(sessionMapper.updatePdfStatus(eq(id), anyString())).thenReturn(1);

        byte[] pdf = service.generatePdf(id);

        assertThat(pdf).isNotEmpty();
        // GENERATING 이후 READY 로 마지막 전이
        verify(sessionMapper).updatePdfStatus(id, "GENERATING");
        verify(sessionMapper).updatePdfStatus(id, "READY");
    }
}
