package kr.co.ircp.cms.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import kr.co.ircp.cms.domain.ai.dto.SimulationResultDto;
import kr.co.ircp.cms.domain.ai.dto.SimulationStartDto;
import kr.co.ircp.cms.domain.ai.exception.AiRateLimitExceededException;
import kr.co.ircp.cms.domain.ai.exception.AiSimulationNotFoundException;
import kr.co.ircp.cms.domain.ai.mapper.AiSimulationSessionMapper;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.domain.ai.model.AiSimulationSession;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 시뮬레이션 세션 서비스 구현.
 *
 * <p>SPEC-CMS-AI-001 — 평문 IP 미저장(SHA-256 hash만 인자로 전달받음),
 * ip-hash 기준 시간당 레이트리밋, 만료 세션 차단.
 */
// @MX:NOTE: [AUTO] SimulationServiceImpl — ipHash는 호출부에서 해시된 값만 수신 (평문 IP 미수신 불변식)
// @MX:SPEC: SPEC-CMS-AI-001
@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationServiceImpl.class);
    private static final String MODEL_NAME = "simulation-model";
    private static final String TYPE = "SIMULATION";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiSimulationSessionMapper sessionMapper;
    private final MlServiceClient mlServiceClient;
    private final AiPredictionLogService aiPredictionLogService;
    private final PdfGeneratorService pdfGeneratorService;
    private final int rateLimitPerHour;

    /**
     * 운영 빈 생성자 — rate-limit과 PDF 생성기를 주입한다.
     *
     * <p>{@code @Autowired} — 본 클래스는 생성자 2개(운영/테스트 편의)를 가지므로
     * Spring이 주입 대상 생성자를 결정하도록 명시한다(다중 생성자 모호성 해소).
     */
    @Autowired
    public SimulationServiceImpl(AiSimulationSessionMapper sessionMapper,
                                 MlServiceClient mlServiceClient,
                                 AiPredictionLogService aiPredictionLogService,
                                 PdfGeneratorService pdfGeneratorService,
                                 @Value("${ai.rate-limit.simulation-per-hour:30}")
                                 int rateLimitPerHour) {
        this.sessionMapper = sessionMapper;
        this.mlServiceClient = mlServiceClient;
        this.aiPredictionLogService = aiPredictionLogService;
        this.pdfGeneratorService = pdfGeneratorService;
        this.rateLimitPerHour = rateLimitPerHour;
    }

    /**
     * 테스트 편의 생성자 — 기본 PdfGeneratorService를 사용한다.
     */
    public SimulationServiceImpl(AiSimulationSessionMapper sessionMapper,
                                 MlServiceClient mlServiceClient,
                                 AiPredictionLogService aiPredictionLogService,
                                 int rateLimitPerHour) {
        this(sessionMapper, mlServiceClient, aiPredictionLogService,
                new PdfGeneratorService(), rateLimitPerHour);
    }

    @Override
    @Transactional
    public SimulationResultDto start(SimulationStartDto dto, String ipHash) {
        // 레이트리밋 — 최근 1시간 동일 ip-hash 요청 수
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        long recent = sessionMapper.countByIpHashSince(ipHash, since);
        if (recent >= rateLimitPerHour) {
            throw new AiRateLimitExceededException(rateLimitPerHour);
        }

        SimulationRequest request = new SimulationRequest(
                dto.ksicCode(), dto.capitalAmount(),
                dto.foundingYear(), dto.revenueAmount());

        String projectionJson;
        String logStatus;
        long start = System.currentTimeMillis();
        try {
            SimulationResponse resp = mlServiceClient.predictSimulation(request);
            projectionJson = toJson(resp);
            logStatus = "SUCCESS";
        } catch (CallNotPermittedException | MlServiceException e) {
            log.warn("ML 시뮬레이션 호출 실패 — 빈 투영으로 폴백: {}", e.getMessage());
            projectionJson = "{\"projection\":[]}";
            logStatus = "FALLBACK";
        }

        AiSimulationSession session = AiSimulationSession.builder()
                .ksicCode(dto.ksicCode())
                .capitalAmount(dto.capitalAmount())
                .foundingYear(dto.foundingYear())
                .revenueAmount(dto.revenueAmount())
                .projectionResult(projectionJson)
                .pdfStatus("NONE")
                .clientIpHash(ipHash)          // 평문 IP 절대 저장 금지 — 해시 값만
                .createdAt(Instant.now())
                .build();
        sessionMapper.insert(session);

        aiPredictionLogService.logAsync(AiPredictionLog.builder()
                .predictionType(TYPE)
                .modelName(MODEL_NAME)
                .modelVersion("rule-v1.0.0")
                .inputFeatures(toJson(dto))
                .outputResult(projectionJson)
                .latencyMs((int) (System.currentTimeMillis() - start))
                .status(logStatus)
                .predictedAt(Instant.now())
                .build());

        return new SimulationResultDto(
                session.getId(), session.getPdfStatus(), projectionJson);
    }

    @Override
    @Transactional(readOnly = true)
    public SimulationResultDto getResult(UUID sessionId) {
        AiSimulationSession session = loadActiveSession(sessionId);
        return new SimulationResultDto(
                session.getId(), session.getPdfStatus(), session.getProjectionResult());
    }

    @Override
    @Transactional
    public byte[] generatePdf(UUID sessionId) {
        AiSimulationSession session = loadActiveSession(sessionId);
        sessionMapper.updatePdfStatus(sessionId, "GENERATING");
        try {
            byte[] pdf = pdfGeneratorService.generateSimulationReport(session);
            sessionMapper.updatePdfStatus(sessionId, "READY");
            return pdf;
        } catch (RuntimeException e) {
            sessionMapper.updatePdfStatus(sessionId, "FAILED");
            throw e;
        }
    }

    /** 세션 조회 + 만료 가드 (미존재/만료 → 404). */
    private AiSimulationSession loadActiveSession(UUID sessionId) {
        AiSimulationSession session = sessionMapper.findById(sessionId)
                .orElseThrow(() -> new AiSimulationNotFoundException(sessionId));
        if (session.getExpiresAt() != null
                && session.getExpiresAt().isBefore(Instant.now())) {
            throw new AiSimulationNotFoundException(sessionId);
        }
        return session;
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
