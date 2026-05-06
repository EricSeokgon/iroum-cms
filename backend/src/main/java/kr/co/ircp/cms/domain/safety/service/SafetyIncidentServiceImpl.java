package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.IncidentCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.IncidentDetail;
import kr.co.ircp.cms.domain.safety.dto.IncidentSummary;
import kr.co.ircp.cms.domain.safety.dto.IncidentUpdateRequest;
import kr.co.ircp.cms.domain.safety.dto.SyncResult;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncident;
import kr.co.ircp.cms.domain.safety.exception.SafetyIncidentNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 사고사례 서비스 구현.
 * REQ-SAFETY-001
 *
 * // @MX:NOTE: [AUTO] 외부 동기화는 1차 mock 구현. v0.2+에서 KOSHA OpenAPI 실 호출 + Spring Retry.
 * // @MX:SPEC: REQ-SAFETY-001-D-1
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyIncidentServiceImpl implements SafetyIncidentService {

    private final SafetyIncidentMapper incidentMapper;

    @Override
    public PageResponse<IncidentSummary> listIncidents(
            String industryCode, String incidentType, String severity,
            int page, int size) {
        int offset = page * size;
        List<SafetyIncident> rows = incidentMapper.findFiltered(industryCode, incidentType, severity, offset, size);
        long total = incidentMapper.countFiltered(industryCode, incidentType, severity);
        List<IncidentSummary> content = rows.stream().map(this::toSummary).collect(Collectors.toList());
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public IncidentDetail getIncident(Long id) {
        SafetyIncident incident = incidentMapper.findById(id)
                .orElseThrow(() -> new SafetyIncidentNotFoundException(id));
        return toDetail(incident);
    }

    @Override
    @Transactional
    public IncidentDetail createIncident(IncidentCreateRequest request) {
        SafetyIncident incident = SafetyIncident.builder()
                .sourceType(request.sourceType())
                .industryCode(request.industryCode())
                .occupationCode(request.occupationCode())
                .processType(request.processType())
                .incidentType(request.incidentType())
                .occurredAt(request.occurredAt())
                .severity(request.severity())
                .casualties(request.casualties() == null ? 0 : request.casualties())
                .location(request.location())
                .summary(request.summary())
                .detailedCause(request.detailedCause())
                .preventionLesson(request.preventionLesson())
                .sourceUrl(request.sourceUrl())
                .status("PUBLISHED")
                .build();
        incidentMapper.insert(incident);
        return toDetail(incident);
    }

    @Override
    @Transactional
    public IncidentDetail updateIncident(Long id, IncidentUpdateRequest request) {
        SafetyIncident existing = incidentMapper.findById(id)
                .orElseThrow(() -> new SafetyIncidentNotFoundException(id));
        SafetyIncident patch = SafetyIncident.builder()
                .id(id)
                .industryCode(request.industryCode())
                .occupationCode(request.occupationCode())
                .processType(request.processType())
                .incidentType(request.incidentType())
                .occurredAt(request.occurredAt())
                .severity(request.severity())
                .casualties(request.casualties() == null ? existing.getCasualties() : request.casualties())
                .location(request.location())
                .summary(request.summary())
                .detailedCause(request.detailedCause())
                .preventionLesson(request.preventionLesson())
                .status(request.status())
                .build();
        incidentMapper.update(patch);
        return toDetail(incidentMapper.findById(id).orElseThrow());
    }

    @Override
    @Transactional
    public void archiveIncident(Long id) {
        incidentMapper.findById(id).orElseThrow(() -> new SafetyIncidentNotFoundException(id));
        incidentMapper.archiveById(id);
    }

    /**
     * 외부 동기화 mock 구현.
     * REQ-SAFETY-001-D-1, D-4: 실패 시 fallback (이전 버전 유지).
     * 1차 단계에서는 mock 결과만 반환. 실 호출은 후속 트랙.
     */
    @Override
    @Transactional
    public SyncResult triggerExternalSync(String sourceType) {
        // @MX:TODO: [AUTO] KOSHA OpenAPI 실 호출 + 정제 파이프라인 + 키워드 추출 통합 (v0.2+)
        return new SyncResult(0, 0, 0, "동기화 mock 완료. sourceType=" + sourceType);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private IncidentSummary toSummary(SafetyIncident i) {
        return new IncidentSummary(
                i.getId(), i.getSourceType(), i.getIndustryCode(),
                i.getIncidentType(), i.getSeverity(), i.getOccurredAt(),
                i.getCasualties(), i.getLocation(), i.getSummary(), i.getStatus()
        );
    }

    private IncidentDetail toDetail(SafetyIncident i) {
        return new IncidentDetail(
                i.getId(), i.getSourceType(), i.getIndustryCode(),
                i.getOccupationCode(), i.getProcessType(), i.getIncidentType(),
                i.getOccurredAt(), i.getSeverity(), i.getCasualties(), i.getLocation(),
                i.getSummary(), i.getDetailedCause(), i.getPreventionLesson(),
                i.getSourceUrl(), i.getStatus(), i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
