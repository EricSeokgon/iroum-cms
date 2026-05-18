package kr.co.ircp.cms.domain.ai.controller;

import kr.co.ircp.cms.domain.ai.dto.AiDriftAlertDto;
import kr.co.ircp.cms.domain.ai.dto.AiMetricDto;
import kr.co.ircp.cms.domain.ai.dto.RetrainRequestDto;
import kr.co.ircp.cms.domain.ai.dto.RetrainStatusDto;
import kr.co.ircp.cms.domain.ai.mapper.AiRetrainQueueMapper;
import kr.co.ircp.cms.domain.ai.mapper.AiSimulationSessionMapper;
import kr.co.ircp.cms.domain.ai.model.AiRetrainQueue;
import kr.co.ircp.cms.domain.ai.service.AiModelMetricService;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AI 운영자 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-001 — 모든 엔드포인트 ROLE=ADMIN 필수(@PreAuthorize)이며
 * @AuditLog로 운영자 행위가 감사 로그에 자동 적재된다(AuditLogAspect).
 */
// @MX:ANCHOR: [AUTO] AiAdminController — 모든 메서드 ADMIN 전용 + 감사 로그 대상
// @MX:REASON: 보안 경계(권한+감사). 메서드 다수가 @PreAuthorize/@AuditLog 계약 공유 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-AI-001
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiAdminController {

    private final AiModelMetricService metricService;
    private final AiRetrainQueueMapper retrainQueueMapper;
    private final AiSimulationSessionMapper simulationSessionMapper;
    private final MlServiceClient mlServiceClient;

    @GetMapping("/metrics")
    @AuditLog(action = "READ", entityType = "AiModelMetric")
    public ResponseEntity<List<AiMetricDto>> metrics(
            @RequestParam(name = "predictionType", required = false) String predictionType,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(metricService.findMetrics(predictionType, limit));
    }

    @GetMapping("/drift-alerts")
    @AuditLog(action = "READ", entityType = "AiDriftAlert", severity = "WARN")
    public ResponseEntity<List<AiDriftAlertDto>> driftAlerts() {
        return ResponseEntity.ok(metricService.findDriftAlerts());
    }

    @GetMapping("/retrain-queue")
    @AuditLog(action = "READ", entityType = "AiRetrainQueue")
    public ResponseEntity<List<RetrainStatusDto>> retrainQueue() {
        return ResponseEntity.ok(retrainQueueMapper.findQueued().stream()
                .map(RetrainStatusDto::from)
                .toList());
    }

    @PostMapping("/retrain-queue")
    @AuditLog(action = "CREATE", entityType = "AiRetrainQueue", severity = "WARN",
            captureArgs = true)
    public ResponseEntity<RetrainStatusDto> requestRetrain(
            @RequestBody RetrainRequestDto request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        AiRetrainQueue item = AiRetrainQueue.builder()
                .modelName(request.modelName())
                .triggerReason("MANUAL")
                .triggerDetail(request.triggerDetail())
                .status("QUEUED")
                .requestedBy(principal != null ? principal.userId() : null)
                .requestedAt(Instant.now())
                .build();
        retrainQueueMapper.insert(item);
        return ResponseEntity.status(201).body(RetrainStatusDto.from(item));
    }

    @PutMapping("/retrain-queue/{id}/status")
    @AuditLog(action = "UPDATE", entityType = "AiRetrainQueue", severity = "WARN")
    public ResponseEntity<Void> updateRetrainStatus(
            @PathVariable("id") Long id,
            @RequestParam(name = "status") String status) {
        int updated = retrainQueueMapper.updateStatus(id, status);
        return updated > 0
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/model-health")
    @AuditLog(action = "READ", entityType = "MlService")
    public ResponseEntity<MlHealthResponse> modelHealth() {
        try {
            return ResponseEntity.ok(mlServiceClient.health());
        } catch (MlServiceException e) {
            return ResponseEntity.status(503)
                    .body(new MlHealthResponse("DOWN", List.of()));
        }
    }

    @PostMapping("/metrics/aggregate")
    @AuditLog(action = "BATCH", entityType = "AiModelMetric", severity = "WARN")
    public ResponseEntity<Void> aggregate(
            @RequestParam(name = "predictionType") String predictionType,
            @RequestParam(name = "date")
            @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        metricService.aggregate(predictionType, date);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/simulation-stats")
    @AuditLog(action = "READ", entityType = "AiSimulationSession")
    public ResponseEntity<Map<String, Object>> simulationStats() {
        // Step 1 매퍼는 통계 집계 쿼리를 제공하지 않으므로 큐 기반 요약만 노출.
        return ResponseEntity.ok(Map.of(
                "queuedRetrains", retrainQueueMapper.findQueued().size()));
    }
}
