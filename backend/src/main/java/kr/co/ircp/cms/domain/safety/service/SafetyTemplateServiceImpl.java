package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.ChecklistItemRequest;
import kr.co.ircp.cms.domain.safety.dto.ChecklistItemResponse;
import kr.co.ircp.cms.domain.safety.dto.PreviewRequest;
import kr.co.ircp.cms.domain.safety.dto.PreviewResponse;
import kr.co.ircp.cms.domain.safety.dto.TemplateRequest;
import kr.co.ircp.cms.domain.safety.dto.TemplateResponse;
import kr.co.ircp.cms.domain.safety.entity.SafetyChecklistItem;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;
import kr.co.ircp.cms.domain.safety.exception.SafetyTemplateNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyChecklistItemMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 가이드라인 템플릿 관리 서비스 구현.
 * REQ-SAFETY-005
 *
 * // @MX:NOTE: [AUTO] 버전 자동 bump(semver) 정책: v1.0 → v1.1 (minor). breaking change는 수동으로 v2.0 지정.
 * // @MX:SPEC: REQ-SAFETY-005-D-2
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyTemplateServiceImpl implements SafetyTemplateService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^v(\\d+)\\.(\\d+)$");

    private final SafetyGuidelineTemplateMapper templateMapper;
    private final SafetyChecklistItemMapper itemMapper;

    @Override
    public List<TemplateResponse> listTemplates() {
        return templateMapper.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public TemplateResponse getTemplate(Long id) {
        SafetyGuidelineTemplate t = templateMapper.findById(id)
                .orElseThrow(() -> new SafetyTemplateNotFoundException(id));
        return toResponse(t);
    }

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateRequest request, Long createdBy) {
        SafetyGuidelineTemplate t = SafetyGuidelineTemplate.builder()
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .applicableIndustryCodes(request.applicableIndustryCodes())
                .applicableGrades(request.applicableGrades())
                .structure(request.structure())
                .reviewStatus(request.reviewStatus())
                .status("DRAFT")
                .version("v1.0")
                .createdBy(createdBy)
                .build();
        templateMapper.insert(t);
        return toResponse(t);
    }

    @Override
    @Transactional
    public TemplateResponse releaseNewVersion(Long id, TemplateRequest request, Long createdBy) {
        SafetyGuidelineTemplate existing = templateMapper.findById(id)
                .orElseThrow(() -> new SafetyTemplateNotFoundException(id));

        // 동일 code의 PUBLISHED 모두 ARCHIVED 전환
        templateMapper.archivePublishedByCode(existing.getCode());

        String nextVersion = bumpMinor(existing.getVersion());
        SafetyGuidelineTemplate next = SafetyGuidelineTemplate.builder()
                .code(existing.getCode())
                .name(request.name() != null ? request.name() : existing.getName())
                .description(request.description() != null ? request.description() : existing.getDescription())
                .applicableIndustryCodes(
                        request.applicableIndustryCodes() == null
                                ? existing.getApplicableIndustryCodes()
                                : request.applicableIndustryCodes())
                .applicableGrades(
                        request.applicableGrades() == null
                                ? existing.getApplicableGrades()
                                : request.applicableGrades())
                .structure(request.structure() != null ? request.structure() : existing.getStructure())
                .reviewStatus(request.reviewStatus())
                .status("PUBLISHED")
                .version(nextVersion)
                .createdBy(createdBy)
                .build();
        templateMapper.insert(next);
        return toResponse(next);
    }

    @Override
    @Transactional
    public void archiveTemplate(Long id) {
        templateMapper.findById(id).orElseThrow(() -> new SafetyTemplateNotFoundException(id));
        templateMapper.archiveById(id);
    }

    @Override
    public PreviewResponse previewTemplate(Long id, PreviewRequest request) {
        SafetyGuidelineTemplate t = templateMapper.findById(id)
                .orElseThrow(() -> new SafetyTemplateNotFoundException(id));
        String html = "<!DOCTYPE html><html><body>"
                + "<h1>" + escape(t.getName()) + "</h1>"
                + "<p>업종: " + escape(request == null ? "" : request.industryCode())
                + ", 등급: " + escape(request == null ? "" : request.riskGrade()) + "</p>"
                + "<p>" + escape(t.getDescription()) + "</p>"
                + "<small>버전: " + escape(t.getVersion()) + " (미리보기, 저장 안 됨)</small>"
                + "</body></html>";
        return new PreviewResponse(html);
    }

    @Override
    public List<ChecklistItemResponse> listChecklistItems(Long templateId) {
        templateMapper.findById(templateId).orElseThrow(() -> new SafetyTemplateNotFoundException(templateId));
        return itemMapper.findByTemplateId(templateId).stream()
                .map(this::toItemResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChecklistItemResponse addChecklistItem(Long templateId, ChecklistItemRequest request) {
        templateMapper.findById(templateId).orElseThrow(() -> new SafetyTemplateNotFoundException(templateId));
        SafetyChecklistItem item = SafetyChecklistItem.builder()
                .templateId(templateId)
                .category(request.category())
                .itemText(request.itemText())
                .severity(request.severity() == null ? "NORMAL" : request.severity())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .status("ACTIVE")
                .build();
        itemMapper.insert(item);
        return toItemResponse(item);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /** semver minor bump: v1.0 → v1.1, v2.3 → v2.4. 비표준 입력 시 v{n+1}.0. */
    static String bumpMinor(String current) {
        if (current == null) return "v1.1";
        Matcher m = VERSION_PATTERN.matcher(current);
        if (m.matches()) {
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            return "v" + major + "." + (minor + 1);
        }
        return "v1.1";
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private TemplateResponse toResponse(SafetyGuidelineTemplate t) {
        return new TemplateResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getApplicableIndustryCodes(), t.getApplicableGrades(),
                t.getStructure(), t.getStatus(), t.getVersion(), t.getReviewStatus(), t.getCreatedAt());
    }

    private ChecklistItemResponse toItemResponse(SafetyChecklistItem item) {
        return new ChecklistItemResponse(item.getId(), item.getTemplateId(),
                item.getCategory(), item.getItemText(), item.getSeverity(),
                item.getSortOrder(), item.getStatus());
    }
}
