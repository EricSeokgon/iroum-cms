package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.MatchResponse;
import kr.co.ircp.cms.domain.safety.dto.ReportCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.ReportDetail;
import kr.co.ircp.cms.domain.safety.dto.ReportSummary;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineReport;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyReportNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyTemplateNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineReportMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 가이드라인 자동 생성 서비스 구현.
 * REQ-SAFETY-003: 매칭 결과 + 템플릿 → HTML 렌더링 → 보고서 저장.
 *
 * // @MX:NOTE: [AUTO] 1차 변수 치환은 String.replace 기반 간이 구현. v0.2+에서 Mustache로 교체.
 * // @MX:SPEC: REQ-SAFETY-003-D-2
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyGuidelineServiceImpl implements SafetyGuidelineService {

    private final CompanySafetyProfileMapper profileMapper;
    private final SafetyGuidelineTemplateMapper templateMapper;
    private final SafetyGuidelineReportMapper reportMapper;
    private final SafetyMatchingService matchingService;

    @Override
    @Transactional
    public ReportDetail generateReport(Long companyId, ReportCreateRequest request) {
        // 1) 프로필 조회
        CompanySafetyProfile profile = profileMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new SafetyProfileNotFoundException(companyId));

        // 2) 템플릿 선택 (REQ-SAFETY-003-D-1)
        SafetyGuidelineTemplate template;
        if (request != null && request.templateId() != null) {
            template = templateMapper.findById(request.templateId())
                    .orElseThrow(() -> new SafetyTemplateNotFoundException(request.templateId()));
        } else {
            template = templateMapper.findLatestPublishedFor(profile.getIndustryCode(), profile.getRiskGrade())
                    .orElseThrow(() -> new SafetyTemplateNotFoundException(
                            "적용 가능한 PUBLISHED 템플릿이 없습니다. industryCode="
                                    + profile.getIndustryCode() + ", grade=" + profile.getRiskGrade()));
        }

        // 3) 매칭 결과 (TOP 5 default) + 변수 치환
        MatchResponse match = matchingService.matchForCompany(companyId, 5);
        String matchedJson = serializeMatched(match);
        String html = renderTemplate(template, profile, match);

        // 4) 저장
        SafetyGuidelineReport report = SafetyGuidelineReport.builder()
                .companyProfileId(profile.getId())
                .templateId(template.getId())
                .riskGrade(profile.getRiskGrade() == null ? "C" : profile.getRiskGrade())
                .matchedIncidentsJsonb(matchedJson)
                .contentHtml(html)
                .build();
        reportMapper.insert(report);

        return toDetail(reportMapper.findById(report.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public ReportDetail getReport(UUID uuid, boolean isAdmin, Long companyId) {
        SafetyGuidelineReport report = reportMapper.findByUuid(uuid)
                .orElseThrow(() -> new SafetyReportNotFoundException(uuid));
        ensureAccess(report, isAdmin, companyId);
        reportMapper.incrementAccessedCount(uuid);
        return toDetail(report);
    }

    @Override
    public String getReportPdfPath(UUID uuid, boolean isAdmin, Long companyId) {
        SafetyGuidelineReport report = reportMapper.findByUuid(uuid)
                .orElseThrow(() -> new SafetyReportNotFoundException(uuid));
        ensureAccess(report, isAdmin, companyId);
        return report.getContentPdfPath();
    }

    @Override
    public PageResponse<ReportSummary> listMyReports(Long companyId, int page, int size) {
        CompanySafetyProfile profile = profileMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new SafetyProfileNotFoundException(companyId));
        int offset = page * size;
        List<SafetyGuidelineReport> rows = reportMapper.findByCompanyProfileId(profile.getId(), offset, size);
        long total = reportMapper.countByCompanyProfileId(profile.getId());
        return PageResponse.of(rows.stream().map(this::toSummary).collect(Collectors.toList()),
                page, size, total);
    }

    @Override
    public PageResponse<ReportSummary> listAllReports(int page, int size) {
        int offset = page * size;
        List<SafetyGuidelineReport> rows = reportMapper.findAll(offset, size);
        long total = reportMapper.countAll();
        return PageResponse.of(rows.stream().map(this::toSummary).collect(Collectors.toList()),
                page, size, total);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private void ensureAccess(SafetyGuidelineReport report, boolean isAdmin, Long companyId) {
        if (isAdmin) return;
        // 비-관리자는 본인 프로필 보고서만 조회 가능
        CompanySafetyProfile profile = profileMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new SafetyProfileNotFoundException(companyId));
        if (!profile.getId().equals(report.getCompanyProfileId())) {
            throw new AccessDeniedException("본인 회사 프로필 보고서만 조회 가능합니다.");
        }
    }

    private String serializeMatched(MatchResponse match) {
        if (match == null || match.results() == null || match.results().isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var mi : match.results()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"incidentId\":").append(mi.incidentId())
              .append(",\"score\":").append(mi.similarityScore())
              .append(",\"summary\":\"")
              .append(mi.summary() == null ? "" : mi.summary().replace("\"", "\\\""))
              .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * REQ-SAFETY-003-D-2: 변수 치환.
     * Handlebars 호환 엔진 도입 전 1차 String.replace 기반.
     */
    String renderTemplate(SafetyGuidelineTemplate template,
                          CompanySafetyProfile profile,
                          MatchResponse match) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"utf-8\">")
            .append("<title>").append(escape(template.getName())).append("</title></head><body>");
        html.append("<h1>").append(escape(template.getName())).append("</h1>");
        html.append("<section><h2>기업 안전 현황 요약</h2>")
            .append("<p>업종: ").append(escape(profile.getIndustryCode()))
            .append(", 리스크 등급: ").append(escape(profile.getRiskGrade()))
            .append(", 종업원 수: ").append(profile.getEmployeeCount() == null ? "-" : profile.getEmployeeCount())
            .append("</p></section>");

        html.append("<section><h2>유사 사고사례</h2><ol>");
        if (match != null && match.results() != null) {
            for (var mi : match.results()) {
                html.append("<li>")
                    .append(escape(mi.incidentType()))
                    .append(" (").append(escape(mi.severity())).append(") ")
                    .append("score=").append(mi.similarityScore())
                    .append(": ").append(escape(mi.summary()))
                    .append("</li>");
            }
        }
        html.append("</ol></section>");

        html.append("<section><h2>예방 가이드라인</h2><p>").append(escape(template.getDescription())).append("</p></section>");
        html.append("<section><h2>버전</h2><p>").append(escape(template.getVersion())).append("</p></section>");
        html.append("</body></html>");
        return html.toString();
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private ReportSummary toSummary(SafetyGuidelineReport r) {
        return new ReportSummary(r.getId(), r.getUuid(), r.getCompanyProfileId(),
                r.getTemplateId(), r.getRiskGrade(), r.getGeneratedAt(), r.getAccessedCount());
    }

    private ReportDetail toDetail(SafetyGuidelineReport r) {
        return new ReportDetail(r.getId(), r.getUuid(), r.getCompanyProfileId(),
                r.getTemplateId(), r.getRiskGrade(), r.getMatchedIncidentsJsonb(),
                r.getContentHtml(), r.getContentPdfPath(), r.getGeneratedAt(), r.getAccessedCount());
    }
}
