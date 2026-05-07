package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.CheckResultRequest;
import kr.co.ircp.cms.domain.safety.dto.CheckResultResponse;
import kr.co.ircp.cms.domain.safety.dto.ChecklistStatsResponse;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.entity.SafetyCheckResult;
import kr.co.ircp.cms.domain.safety.entity.SafetyChecklistItem;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineReport;
import kr.co.ircp.cms.domain.safety.exception.SafetyChecklistItemNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.exception.SafetyReportNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyCheckResultMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyChecklistItemMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAFETY-004: 체크리스트 추적 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyChecklistServiceImpl — REQ-SAFETY-004")
class SafetyChecklistServiceImplTest {

    @Mock private SafetyGuidelineReportMapper reportMapper;
    @Mock private SafetyChecklistItemMapper itemMapper;
    @Mock private SafetyCheckResultMapper checkResultMapper;
    @Mock private CompanySafetyProfileMapper profileMapper;

    @InjectMocks
    private SafetyChecklistServiceImpl service;

    private final UUID reportUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private SafetyGuidelineReport sampleReport() {
        return SafetyGuidelineReport.builder()
                .id(100L).uuid(reportUuid).companyProfileId(50L)
                .templateId(1L).riskGrade("D")
                .build();
    }

    private CompanySafetyProfile sampleProfile() {
        return CompanySafetyProfile.builder()
                .id(50L).companyId(10L).industryCode("F4521").build();
    }

    private SafetyChecklistItem sampleItem() {
        return SafetyChecklistItem.builder()
                .id(20L).templateId(1L).category("PPE").itemText("안전모 착용")
                .severity("HIGH").sortOrder(1).status("ACTIVE")
                .build();
    }

    // ──────────────────────────────────────────────
    // getChecklistByReport
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("관리자가 체크리스트 조회 시 ensureAccess 통과")
    void getChecklistByReport_adminAllowed() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        List<CheckResultResponse> stub = List.of(
                new CheckResultResponse(20L, "PPE", "안전모", "HIGH",
                        "DONE", "evidence", null, 1L, Instant.now())
        );
        when(checkResultMapper.findChecklistWithStatusByReportId(100L)).thenReturn(stub);

        List<CheckResultResponse> result = service.getChecklistByReport(reportUuid, true, null);

        assertThat(result).hasSize(1);
        verify(profileMapper, never()).findByCompanyId(any());
    }

    @Test
    @DisplayName("본인 회사 프로필 보고서일 때 일반 사용자 접근 허용")
    void getChecklistByReport_companyOwnerAllowed() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(checkResultMapper.findChecklistWithStatusByReportId(100L)).thenReturn(List.of());

        List<CheckResultResponse> result = service.getChecklistByReport(reportUuid, false, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("타 회사 프로필 보고서 시 AccessDeniedException")
    void getChecklistByReport_otherCompany_accessDenied() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        CompanySafetyProfile otherProfile = CompanySafetyProfile.builder()
                .id(999L).companyId(20L).build();
        when(profileMapper.findByCompanyId(20L)).thenReturn(Optional.of(otherProfile));

        assertThatThrownBy(() -> service.getChecklistByReport(reportUuid, false, 20L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("보고서 미존재 시 SafetyReportNotFoundException")
    void getChecklistByReport_missingReport_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getChecklistByReport(reportUuid, true, null))
                .isInstanceOf(SafetyReportNotFoundException.class);
    }

    @Test
    @DisplayName("프로필 미존재 시 SafetyProfileNotFoundException")
    void getChecklistByReport_missingProfile_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getChecklistByReport(reportUuid, false, 10L))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // upsertCheckResult
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("체크 결과 upsert — DONE 상태 + 응답 매핑")
    void upsertCheckResult_savesAndReturnsResponse() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(itemMapper.findById(20L)).thenReturn(Optional.of(sampleItem()));

        CheckResultRequest req = new CheckResultRequest("DONE", "안전모 확인됨", null);
        SafetyCheckResult saved = SafetyCheckResult.builder()
                .id(1L).reportId(100L).itemId(20L).checkedBy(7L)
                .status("DONE").evidenceText("안전모 확인됨")
                .checkedAt(Instant.now())
                .build();
        when(checkResultMapper.findByReportIdAndItemId(100L, 20L)).thenReturn(Optional.of(saved));

        CheckResultResponse response = service.upsertCheckResult(
                reportUuid, 20L, req, 7L, true, null
        );

        verify(checkResultMapper, times(1)).upsert(any(SafetyCheckResult.class));
        assertThat(response.itemId()).isEqualTo(20L);
        assertThat(response.category()).isEqualTo("PPE");
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.evidenceText()).isEqualTo("안전모 확인됨");
        assertThat(response.checkedBy()).isEqualTo(7L);
    }

    @Test
    @DisplayName("체크 결과 upsert — 항목 미존재 시 SafetyChecklistItemNotFoundException")
    void upsertCheckResult_missingItem_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(itemMapper.findById(99L)).thenReturn(Optional.empty());

        CheckResultRequest req = new CheckResultRequest("DONE", null, null);

        assertThatThrownBy(() -> service.upsertCheckResult(
                reportUuid, 99L, req, 7L, true, null
        )).isInstanceOf(SafetyChecklistItemNotFoundException.class);
    }

    @Test
    @DisplayName("체크 결과 upsert — 보고서 미존재 시 SafetyReportNotFoundException")
    void upsertCheckResult_missingReport_throws() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.empty());

        CheckResultRequest req = new CheckResultRequest("DONE", null, null);

        assertThatThrownBy(() -> service.upsertCheckResult(
                reportUuid, 20L, req, 7L, true, null
        )).isInstanceOf(SafetyReportNotFoundException.class);
    }

    @Test
    @DisplayName("체크 결과 upsert — 캐시 미스 시 입력값 그대로 응답")
    void upsertCheckResult_cacheMiss_usesInputValues() {
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(sampleReport()));
        when(itemMapper.findById(20L)).thenReturn(Optional.of(sampleItem()));
        when(checkResultMapper.findByReportIdAndItemId(100L, 20L)).thenReturn(Optional.empty());

        CheckResultRequest req = new CheckResultRequest("IN_PROGRESS", "확인 중", null);

        CheckResultResponse response = service.upsertCheckResult(
                reportUuid, 20L, req, 7L, true, null
        );

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.evidenceText()).isEqualTo("확인 중");
    }

    // ──────────────────────────────────────────────
    // getOverallStats — REQ-SAFETY-004-D-4
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("통계 조회 — 합산 + 완료율 계산")
    void getOverallStats_returnsAggregates() {
        when(checkResultMapper.countDoneAcrossAll()).thenReturn(40L);
        when(checkResultMapper.countInProgressAcrossAll()).thenReturn(20L);
        when(checkResultMapper.countBlockedAcrossAll()).thenReturn(10L);
        when(checkResultMapper.countNaAcrossAll()).thenReturn(5L);
        when(checkResultMapper.countTotalReports()).thenReturn(8L);
        when(checkResultMapper.countTotalChecklistItems()).thenReturn(50L);
        when(checkResultMapper.countTotalCheckResults()).thenReturn(80L);

        ChecklistStatsResponse stats = service.getOverallStats();

        assertThat(stats.totalReports()).isEqualTo(8L);
        assertThat(stats.totalItems()).isEqualTo(50L);
        assertThat(stats.doneCount()).isEqualTo(40L);
        assertThat(stats.inProgressCount()).isEqualTo(20L);
        assertThat(stats.blockedCount()).isEqualTo(10L);
        assertThat(stats.naCount()).isEqualTo(5L);
        assertThat(stats.completionRate()).isEqualTo(40.0 / 80.0);
    }

    @Test
    @DisplayName("통계 조회 — 결과 0건 → 완료율 0.0")
    void getOverallStats_emptyResults_returnsZeroRate() {
        when(checkResultMapper.countDoneAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countInProgressAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countBlockedAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countNaAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countTotalReports()).thenReturn(0L);
        when(checkResultMapper.countTotalChecklistItems()).thenReturn(0L);
        when(checkResultMapper.countTotalCheckResults()).thenReturn(0L);

        ChecklistStatsResponse stats = service.getOverallStats();

        assertThat(stats.completionRate()).isEqualTo(0.0);
    }
}
