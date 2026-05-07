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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SafetyGuidelineService GREEN 단계 테스트.
 * REQ-SAFETY-003 — 가이드라인 자동 생성 (매칭 + 템플릿 → HTML 렌더링 → 보고서 저장).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyGuidelineService GREEN 테스트 (REQ-SAFETY-003)")
class SafetyGuidelineServiceTest {

    @Mock
    private CompanySafetyProfileMapper profileMapper;

    @Mock
    private SafetyGuidelineTemplateMapper templateMapper;

    @Mock
    private SafetyGuidelineReportMapper reportMapper;

    @Mock
    private SafetyMatchingService matchingService;

    private SafetyGuidelineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SafetyGuidelineServiceImpl(
                profileMapper, templateMapper, reportMapper, matchingService);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private CompanySafetyProfile stubProfile(long id, long companyId, String industry, String grade) {
        return CompanySafetyProfile.builder()
                .id(id)
                .companyId(companyId)
                .industryCode(industry)
                .subIndustry("토목")
                .employeeCount(50)
                .primaryProcess("크레인 작업")
                .hazardFactors("[\"고소작업\"]")
                .riskScore(new BigDecimal("75.50"))
                .riskGrade(grade)
                .updatedAt(Instant.now())
                .build();
    }

    private SafetyGuidelineTemplate stubTemplate(long id, String code, String version) {
        return SafetyGuidelineTemplate.builder()
                .id(id)
                .code(code)
                .name("템플릿 " + code)
                .description("설명")
                .applicableIndustryCodes(List.of("F4521"))
                .applicableGrades(List.of("D"))
                .structure("{}")
                .status("PUBLISHED")
                .version(version)
                .reviewStatus("NONE")
                .build();
    }

    private SafetyGuidelineReport stubReport(long id, long companyProfileId, long templateId, String grade) {
        return SafetyGuidelineReport.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .companyProfileId(companyProfileId)
                .templateId(templateId)
                .riskGrade(grade)
                .matchedIncidentsJsonb("[]")
                .contentHtml("<html/>")
                .contentPdfPath("/tmp/report.pdf")
                .generatedAt(Instant.now())
                .accessedCount(0)
                .build();
    }

    private MatchResponse stubMatch(int n) {
        return new MatchResponse(7L, n, false, List.of(
                new MatchedIncident(1L, "F4521", "추락", "HIGH", Instant.now(),
                        "타워크레인 추락 사고", new BigDecimal("0.91"),
                        "{\"contributions\":[\"industry\"]}")
        ));
    }

    private MatchResponse emptyMatch() {
        return new MatchResponse(7L, 5, false, List.of());
    }

    // ──────────────────────────────────────────────
    // generateReport — 정상 + 분기
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("보고서 생성 — templateId 명시 시 해당 템플릿 사용")
    void generateReport_explicitTemplateId_usesSpecifiedTemplate() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        SafetyGuidelineTemplate template = stubTemplate(20L, "T020", "v1.0");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findById(20L)).thenReturn(Optional.of(template));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(stubMatch(5));

        SafetyGuidelineReport saved = stubReport(99L, 7L, 20L, "D");
        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(99L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(99L)).thenReturn(Optional.of(saved));

        ReportCreateRequest req = new ReportCreateRequest(20L);

        // act
        ReportDetail result = service.generateReport(100L, req);

        // assert
        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.templateId()).isEqualTo(20L);
        verify(templateMapper).findById(20L);
        verify(templateMapper, never()).findLatestPublishedFor(any(), any());
    }

    @Test
    @DisplayName("보고서 생성 — templateId=null이면 industryCode/grade로 최신 PUBLISHED 자동 선택")
    void generateReport_nullTemplateId_findsLatestPublished() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        SafetyGuidelineTemplate latest = stubTemplate(30L, "T030", "v2.0");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findLatestPublishedFor("F4521", "D")).thenReturn(Optional.of(latest));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(stubMatch(5));

        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(100L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(100L)).thenReturn(Optional.of(stubReport(100L, 7L, 30L, "D")));

        // act
        service.generateReport(100L, null); // request 자체가 null

        // assert
        verify(templateMapper).findLatestPublishedFor("F4521", "D");
        verify(templateMapper, never()).findById(any());
    }

    @Test
    @DisplayName("보고서 생성 — request는 있지만 templateId=null이면 자동 선택")
    void generateReport_requestWithNullTemplateId_findsLatestPublished() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findLatestPublishedFor("F4521", "D"))
                .thenReturn(Optional.of(stubTemplate(40L, "T040", "v1.0")));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(stubMatch(5));
        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(101L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(101L)).thenReturn(Optional.of(stubReport(101L, 7L, 40L, "D")));

        // request.templateId = null
        ReportCreateRequest req = new ReportCreateRequest(null);

        // act
        service.generateReport(100L, req);

        // assert
        verify(templateMapper).findLatestPublishedFor("F4521", "D");
    }

    @Test
    @DisplayName("보고서 생성 — 프로필 미존재 시 SafetyProfileNotFoundException")
    void generateReport_missingProfile_throwsProfileNotFound() {
        // arrange
        when(profileMapper.findByCompanyId(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.generateReport(999L, new ReportCreateRequest(null)))
                .isInstanceOf(SafetyProfileNotFoundException.class);

        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("보고서 생성 — 명시 템플릿 미존재 시 SafetyTemplateNotFoundException")
    void generateReport_explicitTemplateMissing_throwsTemplateNotFound() {
        // arrange
        when(profileMapper.findByCompanyId(100L))
                .thenReturn(Optional.of(stubProfile(7L, 100L, "F4521", "D")));
        when(templateMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.generateReport(100L, new ReportCreateRequest(999L)))
                .isInstanceOf(SafetyTemplateNotFoundException.class);

        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("보고서 생성 — 적용 가능한 PUBLISHED 템플릿 없으면 SafetyTemplateNotFoundException")
    void generateReport_noPublishedTemplate_throwsTemplateNotFound() {
        // arrange
        when(profileMapper.findByCompanyId(100L))
                .thenReturn(Optional.of(stubProfile(7L, 100L, "F4521", "D")));
        when(templateMapper.findLatestPublishedFor("F4521", "D")).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.generateReport(100L, new ReportCreateRequest(null)))
                .isInstanceOf(SafetyTemplateNotFoundException.class)
                .hasMessageContaining("PUBLISHED");

        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("보고서 생성 — riskGrade null이면 \"C\"로 fallback 저장")
    void generateReport_nullRiskGrade_fallsBackToC() {
        // arrange — riskGrade=null인 프로필
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", null);
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findLatestPublishedFor("F4521", null))
                .thenReturn(Optional.of(stubTemplate(50L, "T050", "v1.0")));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(stubMatch(5));
        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(102L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(102L)).thenReturn(Optional.of(stubReport(102L, 7L, 50L, "C")));

        ArgumentCaptor<SafetyGuidelineReport> captor = ArgumentCaptor.forClass(SafetyGuidelineReport.class);

        // act
        service.generateReport(100L, new ReportCreateRequest(null));

        // assert — fallback "C"
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getRiskGrade()).isEqualTo("C");
    }

    @Test
    @DisplayName("보고서 생성 — 매칭 결과 비어있으면 matchedIncidentsJsonb=\"[]\"")
    void generateReport_emptyMatch_storesEmptyJsonArray() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findLatestPublishedFor("F4521", "D"))
                .thenReturn(Optional.of(stubTemplate(60L, "T060", "v1.0")));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(emptyMatch());
        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(103L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(103L)).thenReturn(Optional.of(stubReport(103L, 7L, 60L, "D")));

        ArgumentCaptor<SafetyGuidelineReport> captor = ArgumentCaptor.forClass(SafetyGuidelineReport.class);

        // act
        service.generateReport(100L, new ReportCreateRequest(null));

        // assert
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getMatchedIncidentsJsonb()).isEqualTo("[]");
    }

    @Test
    @DisplayName("보고서 생성 — 매칭 결과 있으면 incidentId/score/summary가 JSON 직렬화")
    void generateReport_withMatch_serializesMatchedIncidents() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findLatestPublishedFor("F4521", "D"))
                .thenReturn(Optional.of(stubTemplate(70L, "T070", "v1.0")));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(stubMatch(5));
        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(104L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(104L)).thenReturn(Optional.of(stubReport(104L, 7L, 70L, "D")));

        ArgumentCaptor<SafetyGuidelineReport> captor = ArgumentCaptor.forClass(SafetyGuidelineReport.class);

        // act
        service.generateReport(100L, new ReportCreateRequest(null));

        // assert
        verify(reportMapper).insert(captor.capture());
        String json = captor.getValue().getMatchedIncidentsJsonb();
        assertThat(json).contains("\"incidentId\":1");
        assertThat(json).contains("\"score\":0.91");
        assertThat(json).contains("타워크레인 추락 사고");
    }

    @Test
    @DisplayName("보고서 생성 — contentHtml에 템플릿 이름·업종·등급 포함")
    void generateReport_htmlContainsTemplateAndProfileInfo() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        SafetyGuidelineTemplate template = stubTemplate(80L, "T080", "v3.0");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(templateMapper.findLatestPublishedFor("F4521", "D")).thenReturn(Optional.of(template));
        when(matchingService.matchForCompany(100L, 5)).thenReturn(stubMatch(5));
        doAnswer(inv -> {
            SafetyGuidelineReport r = inv.getArgument(0);
            r.setId(105L);
            return null;
        }).when(reportMapper).insert(any(SafetyGuidelineReport.class));
        when(reportMapper.findById(105L)).thenReturn(Optional.of(stubReport(105L, 7L, 80L, "D")));

        ArgumentCaptor<SafetyGuidelineReport> captor = ArgumentCaptor.forClass(SafetyGuidelineReport.class);

        // act
        service.generateReport(100L, new ReportCreateRequest(null));

        // assert
        verify(reportMapper).insert(captor.capture());
        String html = captor.getValue().getContentHtml();
        assertThat(html).contains("템플릿 T080");
        assertThat(html).contains("F4521");
        assertThat(html).contains("D");
        assertThat(html).contains("v3.0");
        assertThat(html).contains("<!DOCTYPE html>");
    }

    // ──────────────────────────────────────────────
    // getReport — 권한 분기
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("보고서 조회 — 관리자는 권한 검사 없이 incrementAccessedCount 호출 후 반환")
    void getReport_admin_incrementsAccessAndReturns() {
        // arrange
        UUID uuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 7L, 10L, "D");
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.of(report));

        // act
        ReportDetail result = service.getReport(uuid, true, 999L);

        // assert
        assertThat(result.id()).isEqualTo(1L);
        verify(reportMapper).incrementAccessedCount(uuid);
        // 관리자는 프로필 조회 안 함
        verify(profileMapper, never()).findByCompanyId(any());
    }

    @Test
    @DisplayName("보고서 조회 — 본인 프로필 보고서면 비-관리자도 조회 가능")
    void getReport_owner_returnsReport() {
        // arrange
        UUID uuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 7L, 10L, "D");
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.of(report));
        when(profileMapper.findByCompanyId(100L))
                .thenReturn(Optional.of(stubProfile(7L, 100L, "F4521", "D")));

        // act
        ReportDetail result = service.getReport(uuid, false, 100L);

        // assert
        assertThat(result.id()).isEqualTo(1L);
        verify(reportMapper).incrementAccessedCount(uuid);
    }

    @Test
    @DisplayName("보고서 조회 — 비-관리자가 타인 보고서 접근 시 AccessDeniedException")
    void getReport_otherCompany_throwsAccessDenied() {
        // arrange
        UUID uuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 7L, 10L, "D");
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.of(report));
        // 요청자의 프로필 ID는 999L (보고서 소유 7L과 다름)
        when(profileMapper.findByCompanyId(999L))
                .thenReturn(Optional.of(stubProfile(999L, 999L, "C2511", "C")));

        // act + assert
        assertThatThrownBy(() -> service.getReport(uuid, false, 999L))
                .isInstanceOf(AccessDeniedException.class);

        verify(reportMapper, never()).incrementAccessedCount(any());
    }

    @Test
    @DisplayName("보고서 조회 — 미존재 UUID는 SafetyReportNotFoundException")
    void getReport_nonExistentUuid_throwsReportNotFound() {
        // arrange
        UUID uuid = UUID.randomUUID();
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getReport(uuid, true, 100L))
                .isInstanceOf(SafetyReportNotFoundException.class);
    }

    @Test
    @DisplayName("보고서 조회 — 비-관리자 + 프로필 미존재 시 SafetyProfileNotFoundException")
    void getReport_nonAdminMissingProfile_throwsProfileNotFound() {
        // arrange
        UUID uuid = UUID.randomUUID();
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.of(stubReport(1L, 7L, 10L, "D")));
        when(profileMapper.findByCompanyId(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getReport(uuid, false, 999L))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // getReportPdfPath
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("PDF 경로 조회 — 관리자는 권한 검사 없이 경로 반환")
    void getReportPdfPath_admin_returnsPath() {
        // arrange
        UUID uuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 7L, 10L, "D");
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.of(report));

        // act
        String result = service.getReportPdfPath(uuid, true, 999L);

        // assert
        assertThat(result).isEqualTo("/tmp/report.pdf");
        // PDF 경로 조회는 incrementAccessedCount 미호출
        verify(reportMapper, never()).incrementAccessedCount(any());
    }

    @Test
    @DisplayName("PDF 경로 조회 — 비-관리자가 타인 보고서 접근 시 AccessDeniedException")
    void getReportPdfPath_otherCompany_throwsAccessDenied() {
        // arrange
        UUID uuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 7L, 10L, "D");
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.of(report));
        when(profileMapper.findByCompanyId(999L))
                .thenReturn(Optional.of(stubProfile(999L, 999L, "C2511", "C")));

        // act + assert
        assertThatThrownBy(() -> service.getReportPdfPath(uuid, false, 999L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("PDF 경로 조회 — 미존재 보고서는 SafetyReportNotFoundException")
    void getReportPdfPath_nonExistentReport_throwsReportNotFound() {
        // arrange
        UUID uuid = UUID.randomUUID();
        when(reportMapper.findByUuid(uuid)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getReportPdfPath(uuid, true, 100L))
                .isInstanceOf(SafetyReportNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // listMyReports
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("내 보고서 목록 — page=2, size=10이면 offset=20으로 조회")
    void listMyReports_calculatesOffsetCorrectly() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "F4521", "D");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));
        when(reportMapper.findByCompanyProfileId(eq(7L), eq(20), eq(10)))
                .thenReturn(List.of(stubReport(1L, 7L, 10L, "D"), stubReport(2L, 7L, 10L, "D")));
        when(reportMapper.countByCompanyProfileId(7L)).thenReturn(25L);

        // act
        PageResponse<ReportSummary> result = service.listMyReports(100L, 2, 10);

        // assert
        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25L);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10)
        verify(reportMapper).findByCompanyProfileId(7L, 20, 10);
    }

    @Test
    @DisplayName("내 보고서 목록 — 빈 결과면 totalElements=0, totalPages=0")
    void listMyReports_emptyResult_returnsEmptyPage() {
        // arrange
        when(profileMapper.findByCompanyId(100L))
                .thenReturn(Optional.of(stubProfile(7L, 100L, "F4521", "D")));
        when(reportMapper.findByCompanyProfileId(eq(7L), eq(0), eq(20))).thenReturn(List.of());
        when(reportMapper.countByCompanyProfileId(7L)).thenReturn(0L);

        // act
        PageResponse<ReportSummary> result = service.listMyReports(100L, 0, 20);

        // assert
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("내 보고서 목록 — 프로필 미존재 시 SafetyProfileNotFoundException")
    void listMyReports_missingProfile_throwsProfileNotFound() {
        // arrange
        when(profileMapper.findByCompanyId(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.listMyReports(999L, 0, 10))
                .isInstanceOf(SafetyProfileNotFoundException.class);

        verify(reportMapper, never()).findByCompanyProfileId(any(), any(Integer.class), any(Integer.class));
    }

    // ──────────────────────────────────────────────
    // listAllReports (관리자)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("전체 보고서 목록 — offset/limit 계산 후 PageResponse 반환")
    void listAllReports_returnsPageResponse() {
        // arrange
        when(reportMapper.findAll(eq(0), eq(20)))
                .thenReturn(List.of(stubReport(1L, 7L, 10L, "D")));
        when(reportMapper.countAll()).thenReturn(5L);

        // act
        PageResponse<ReportSummary> result = service.listAllReports(0, 20);

        // assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(5L);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(reportMapper).findAll(0, 20);
        verify(reportMapper).countAll();
    }

    @Test
    @DisplayName("전체 보고서 목록 — page=3, size=5이면 offset=15")
    void listAllReports_calculatesOffsetCorrectly() {
        // arrange
        when(reportMapper.findAll(eq(15), eq(5))).thenReturn(List.of());
        when(reportMapper.countAll()).thenReturn(20L);

        // act
        PageResponse<ReportSummary> result = service.listAllReports(3, 5);

        // assert
        assertThat(result.totalPages()).isEqualTo(4); // ceil(20/5)
        verify(reportMapper).findAll(15, 5);
    }

    // ──────────────────────────────────────────────
    // 헬퍼 메서드 (escape, renderTemplate)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("escape — HTML 특수문자 이스케이프 (&, <, >, \" 변환)")
    void escape_specialCharacters_escapedProperly() {
        // act + assert
        assertThat(SafetyGuidelineServiceImpl.escape("<script>alert(\"x\")&y</script>"))
                .isEqualTo("&lt;script&gt;alert(&quot;x&quot;)&amp;y&lt;/script&gt;");
        assertThat(SafetyGuidelineServiceImpl.escape(null)).isEqualTo("");
        assertThat(SafetyGuidelineServiceImpl.escape("정상값")).isEqualTo("정상값");
    }
}
