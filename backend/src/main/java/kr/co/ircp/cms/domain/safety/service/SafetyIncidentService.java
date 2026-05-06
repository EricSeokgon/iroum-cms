package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.IncidentCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.IncidentDetail;
import kr.co.ircp.cms.domain.safety.dto.IncidentSummary;
import kr.co.ircp.cms.domain.safety.dto.IncidentUpdateRequest;
import kr.co.ircp.cms.domain.safety.dto.SyncResult;

/**
 * 사고사례 관리 서비스.
 * REQ-SAFETY-001
 */
public interface SafetyIncidentService {

    PageResponse<IncidentSummary> listIncidents(
            String industryCode, String incidentType, String severity,
            int page, int size);

    IncidentDetail getIncident(Long id);

    IncidentDetail createIncident(IncidentCreateRequest request);

    IncidentDetail updateIncident(Long id, IncidentUpdateRequest request);

    void archiveIncident(Long id);

    /** REQ-SAFETY-001-D-1: 외부 데이터(KOSHA OpenAPI / 사고백서) 동기화 트리거. */
    SyncResult triggerExternalSync(String sourceType);
}
