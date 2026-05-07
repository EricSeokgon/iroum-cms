package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.MatchResponse;
import kr.co.ircp.cms.domain.safety.dto.MatchedIncident;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAFETY-003: 가이드라인 자동 생성 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyGuidelineServiceImpl — REQ-SAFETY-003")
class SafetyGuidelineServiceImplTest {

    @Mock private CompanySafetyProfileMapper profileMapper;
    @Mock private SafetyGuidelineTemplateMapper templateMapper;
    @Mock private SafetyGuidelineReportMapper reportMapper;
    @Mock private SafetyMatchingService matchingService;

    @InjectMocks
    private SafetyGuidelineServiceImpl service;

    private final UUID reportUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private CompanySafetyProfile sampleProfile() {
        return CompanySafetyProfile.builder()
                .id(50L).companyId(10L)
                .industryCode("F4521").subIndustry("건설업")
                .employeeCount(100).primaryProcess("고소작업")
                .hazardFactors("[\"추락\"]")
                .riskGrade("D")
                .build();
    }

    private SafetyGuidelineTemplate sampleTemplate(long id, String version) {
        return SafetyGuidelineTemplate.builder()
                .id(id).code("T001").name("건설업 D등급 템플릿")
                .description("템플릿 설명").version(version)
                .status("PUBLISHED")
                .build();
    }

    private SafetyGuidelineReport sampleReport() {
        return SafetyGuidelineReport.builder()
                .id(100L).uuid(reportUuid).companyProfileId(50L)
                .templateId(1L).riskGrade("D")
                .matchedIncidentsJsonb("[]").contentHtml("<html></html>")
                .accessedCount(0)
                .generatedAt(Instant.now())
                .build();
    }

    private MatchResponse sampleMatch() {
        MatchedIncident mi = new MatchedIncident(
                70L, "F4521", "FALL", "FATAL", Instant.now(),
                "사고 요약", new BigDecimal("0.85"), "{\"contributions\":[]}"
        );
        return new MatchResponse(50L, 5, false, List.of(mi));
    }

    // ──────────────────────────────────────────────
    // generateReport — REQ-SAFETY-003-D-1, D-2
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("보고서 생성 — templateId 명시 시 해당 템플릿으로 INSERT")
    void generateReport_withTemplateId_usesSpecifiedTemplate() {
        SafetyGuidelineTemplate template = sampleTemplate(7L, "v1.0");
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(templateMapper.findById(7L)).thenReturn(Optional.of(template));
        when(matchingService.matchForCompany(eq(10L), anyInt())).thenReturn(sampleMatch());
        when(reportMapper.findById(any())).thenReturn(Optional.of(sampleReport()));

        ReportDetail detail = service.generateReport(10L, new ReportCreateRequest(7L));

        ArgumentCaptor<SafetyGuidelineReport> captor = ArgumentCaptor.forClass(SafetyGuidelineReport.class);
        verify(reportMapper, times(1)).insert(captor.capture());
        SafetyGuidelineReport inserted = captor.getValue();
        assertThat(inserted.getCompanyProfileId()).isEqualTo(50L);
        assertThat(inserted.getTemplateId()).isEqualTo(7L);
        assertThat(inserted.getRiskGrade()).isEqualTo("D");
        assertThat(inserted.getContentHtml()).contains("<html");
        assertThat(inserted.getMatchedIncidentsJsonb()).contains("incidentId").contains("70");

        assertThat(detail).isNotNull();
        assertThat(detail.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("보고서 생성 — request null 시 자동 PUBLISHED 템플릿 사용")
    void generateReport_nullRequest_autoSelectsPublishedTemplate() {
        SafetyGuidelineTemplate template = sampleTemplate(8L, "v1.1");
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(templateMapper.findLatestPublishedFor("F4521", "D")).thenReturn(Optional.of(template));
        when(matchingService.matchForCompany(eq(10L), anyInt())).thenReturn(sampleMatch());
        when(reportMapper.findById(any())).thenReturn(Optional.of(sampleReport()));

        service.generateReport(10L, null);

        verify(templateMapper, times(1)).findLatestPublishedFor("F4521", "D");
    }

    @Test
    @DisplayName("보고서 생성 — templateId null 시 자동 PUBLISHED 템플릿 사용")
    void generateReport_nullTemplateId_autoSelectsPublishedTemplate() {
        SafetyGuidelineTemplate template = sampleTemplate(8L, "v1.0");
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(templateMapper.findLatestPublishedFor("F4521", "D")).thenReturn(Optional.of(template));
        when(matchingService.matchForCompany(eq(10L), anyInt())).thenReturn(sampleMatch());
        when(reportMapper.findById(any())).thenReturn(Optional.of(sampleReport()));

        service.generateReport(10L, new ReportCreateRequest(null));

        verify(templateMapper, times(1)).findLatestPublishedFor("F4521", "D");
    }

    @Test
    @DisplayName("보고서 생성 — 자동 선택 템플릿 미존재 시 SafetyTemplateNotFoundException")
    void generateReport_noPublishedTemplate_throws() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(templateMapper.findLatestPublishedFor("F4521", "D")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateReport(10L, new ReportCreateRequest(null)))
                .isInstanceOf(SafetyTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("보고서 생성 — 명시 템플릿 미존재 시 SafetyTemplateNotFoundException")
    void generateReport_explicitTemplateMissing_throws() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(templateMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateReport(10L, new ReportCreateRequest(99L)))
                .isInstanceOf(SafetyTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("보고서 생성 — 프로필 미존재 시 SafetyProfileNotFoundException")
    void generateReport_missingProfile_throws() {
        when(profileMapper.findByCompanyId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateReport(99L, new ReportCreateRequest(null)))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    @Test
    @DisplayName("보고서 생성 — riskGrade null 시 'C' 기본값 적용")
    void generateReport_nullRiskGrade_usesDefaultC() {
        CompanySafetyProfile profile = CompanySafetyProfile.builder()
                .id(50L).companyId(10L).industryCode("F4521")
                .riskGrade(null)
                .build();
        SafetyGuidelineTemplate template = sampleTemplate(7L, "v1.0");
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(profile));
        when(templateMapper.findById(7L)).thenReturn(Optional.of(template));
        when(matchingService.matchForCompany(eq(10L), anyInt()))
                .thenReturn(new MatchResponse(50L, 5, false, List.of()));
        when(reportMapper.findById(any())).thenReturn(Optional.of(sampleReport()));

        service.generateReport(10L, new ReportCreateRequest(7L));

        ArgumentCaptor<SafetyGuidelineReport> captor = ArgumentCaptor.forClass(SafetyGuidelineReport.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getRiskGrade()).isEqualTo("C");
    }

    // ──────────────────────────────────────────────
    // getReport
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("보고서 조회 — 관리자 + 접근 카운터 증가")
    void getReport_adminAccessIncrementsCount() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));

        ReportDetail detail = service.getReport(reportUuid, true, null);

        verify(reportMapper, times(1)).incrementAccessedCount(reportUuid);
        assertThat(detail.uuid()).isEqualTo(reportUuid);
    }

    @Test
    @DisplayName("보고서 조회 — 본인 회사 프로필 일반 사용자 허용")
    void getReport_companyOwnerAllowed() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));

        ReportDetail detail = service.getReport(reportUuid, false, 10L);

        assertThat(detail.id()).isEqualTo(100L);
        verify(reportMapper).incrementAccessedCount(reportUuid);
    }

    @Test
    @DisplayName("보고서 조회 — 타 회사 프로필 시 AccessDeniedException")
    void getReport_otherCompany_accessDenied() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        CompanySafetyProfile other = CompanySafetyProfile.builder().id(999L).companyId(20L).build();
        when(profileMapper.findByCompanyId(20L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.getReport(reportUuid, false, 20L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("보고서 조회 — 미존재 시 SafetyReportNotFoundException")
    void getReport_missing_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReport(reportUuid, true, null))
                .isInstanceOf(SafetyReportNotFoundException.class);
    }

    @Test
    @DisplayName("보고서 조회 — 비-관리자가 프로필 미존재 시 SafetyProfileNotFoundException")
    void getReport_missingProfileForUser_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReport(reportUuid, false, 10L))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // getReportPdfPath
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PDF 경로 조회 — 관리자 접근")
    void getReportPdfPath_admin_returnsPath() {
        SafetyGuidelineReport report = SafetyGuidelineReport.builder()
                .id(100L).uuid(reportUuid).companyProfileId(50L)
                .contentPdfPath("/tmp/report.pdf").build();
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(report));

        String path = service.getReportPdfPath(reportUuid, true, null);

        assertThat(path).isEqualTo("/tmp/report.pdf");
    }

    @Test
    @DisplayName("PDF 경로 조회 — 보고서 미존재 시 SafetyReportNotFoundException")
    void getReportPdfPath_missing_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReportPdfPath(reportUuid, true, null))
                .isInstanceOf(SafetyReportNotFoundException.class);
    }

    @Test
    @DisplayName("PDF 경로 조회 — 타 회사 시 AccessDeniedException")
    void getReportPdfPath_otherCompany_accessDenied() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        CompanySafetyProfile other = CompanySafetyProfile.builder().id(999L).companyId(20L).build();
        when(profileMapper.findByCompanyId(20L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.getReportPdfPath(reportUuid, false, 20L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ──────────────────────────────────────────────
    // listMyReports / listAllReports
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("본인 보고서 목록 — 페이징 응답")
    void listMyReports_returnsPagedResponse() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(reportMapper.findByCompanyProfileId(50L, 0, 10)).thenReturn(List.of(sampleReport()));
        when(reportMapper.countByCompanyProfileId(50L)).thenReturn(1L);

        PageResponse<ReportSummary> page = service.listMyReports(10L, 0, 10);

        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).uuid()).isEqualTo(reportUuid);
    }

    @Test
    @DisplayName("본인 보고서 목록 — 프로필 미존재 시 SafetyProfileNotFoundException")
    void listMyReports_missingProfile_throws() {
        when(profileMapper.findByCompanyId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMyReports(99L, 0, 10))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    @Test
    @DisplayName("전체 보고서 목록 — 관리자용 페이징 응답")
    void listAllReports_returnsPagedResponse() {
        when(reportMapper.findAll(0, 5)).thenReturn(List.of(sampleReport()));
        when(reportMapper.countAll()).thenReturn(1L);

        PageResponse<ReportSummary> page = service.listAllReports(0, 5);

        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("전체 보고서 목록 — 빈 페이지")
    void listAllReports_empty() {
        when(reportMapper.findAll(0, 5)).thenReturn(List.of());
        when(reportMapper.countAll()).thenReturn(0L);

        PageResponse<ReportSummary> page = service.listAllReports(0, 5);

        assertThat(page.totalElements()).isEqualTo(0L);
        assertThat(page.content()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // 헬퍼: escape / renderTemplate
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("escape — null/HTML 특수 문자 처리")
    void escape_handlesNullAndSpecialChars() {
        assertThat(SafetyGuidelineServiceImpl.escape(null)).isEqualTo("");
        assertThat(SafetyGuidelineServiceImpl.escape("<a>&\"")).isEqualTo("&lt;a&gt;&amp;&quot;");
    }

    @Test
    @DisplayName("renderTemplate — HTML 구조 + 사고 요약 포함")
    void renderTemplate_buildsExpectedHtml() {
        SafetyGuidelineTemplate template = sampleTemplate(7L, "v1.0");
        CompanySafetyProfile profile = sampleProfile();
        MatchResponse match = sampleMatch();

        String html = service.renderTemplate(template, profile, match);

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("건설업 D등급 템플릿");
        assertThat(html).contains("F4521");
        assertThat(html).contains("FALL");
        assertThat(html).contains("FATAL");
        assertThat(html).contains("사고 요약");
        assertThat(html).contains("v1.0");
    }

    @Test
    @DisplayName("renderTemplate — 매칭 결과 null 안전 처리")
    void renderTemplate_nullMatch_safeRender() {
        SafetyGuidelineTemplate template = sampleTemplate(7L, "v1.0");
        CompanySafetyProfile profile = sampleProfile();

        String html = service.renderTemplate(template, profile, null);

        assertThat(html).contains("<ol></ol>");
        assertThat(html).contains("v1.0");
    }
}
