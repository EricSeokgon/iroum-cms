package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.ChecklistItemRequest;
import kr.co.ircp.cms.domain.safety.dto.ChecklistItemResponse;
import kr.co.ircp.cms.domain.safety.dto.PreviewRequest;
import kr.co.ircp.cms.domain.safety.dto.PreviewResponse;
import kr.co.ircp.cms.domain.safety.dto.TemplateRequest;
import kr.co.ircp.cms.domain.safety.dto.TemplateResponse;

import java.util.List;

/**
 * 가이드라인 템플릿 관리 서비스.
 * REQ-SAFETY-005
 */
public interface SafetyTemplateService {

    List<TemplateResponse> listTemplates();

    TemplateResponse getTemplate(Long id);

    TemplateResponse createTemplate(TemplateRequest request, Long createdBy);

    /** 신규 버전 발행 (semver 자동 bump v1.0 → v1.1). */
    TemplateResponse releaseNewVersion(Long id, TemplateRequest request, Long createdBy);

    void archiveTemplate(Long id);

    PreviewResponse previewTemplate(Long id, PreviewRequest request);

    // ─── 체크리스트 항목 ─────────────────────────────────────────────────────
    List<ChecklistItemResponse> listChecklistItems(Long templateId);
    ChecklistItemResponse addChecklistItem(Long templateId, ChecklistItemRequest request);
}
