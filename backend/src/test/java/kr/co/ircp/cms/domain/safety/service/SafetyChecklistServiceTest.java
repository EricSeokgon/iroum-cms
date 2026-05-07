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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SafetyChecklistService GREEN 단계 테스트.
 * REQ-SAFETY-004 — 체크리스트 추적 + 통계.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyChecklistService GREEN 테스트 (REQ-SAFETY-004)")
class SafetyChecklistServiceTest {

    @Mock
    private SafetyGuidelineReportMapper reportMapper;

    @Mock
    private SafetyChecklistItemMapper itemMapper;

    @Mock
    private SafetyCheckResultMapper checkResultMapper;

    @Mock
    private CompanySafetyProfileMapper profileMapper;

    private SafetyChecklistService service;

    @BeforeEach
    void setUp() {
        service = new SafetyChecklistServiceImpl(reportMapper, itemMapper, checkResultMapper, profileMapper);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private SafetyGuidelineReport stubReport(long id, long companyProfileId) {
        return SafetyGuidelineReport.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .companyProfileId(companyProfileId)
                .templateId(10L)
                .riskGrade("D")
                .matchedIncidentsJsonb("[]")
                .contentHtml("<html/>")
                .generatedAt(Instant.now())
                .accessedCount(0)
                .build();
    }

    private SafetyChecklistItem stubItem(long id, String severity) {
        return SafetyChecklistItem.builder()
                .id(id)
                .templateId(10L)
                .category("PPE")
                .itemText("안전모 착용 점검")
                .severity(severity)
                .sortOrder(1)
                .status("ACTIVE")
                .build();
    }

    private SafetyCheckResult stubResult(long reportId, long itemId, String status) {
        return SafetyCheckResult.builder()
                .id(99L)
                .reportId(reportId)
                .itemId(itemId)
                .checkedBy(50L)
                .status(status)
                .evidenceText("증거 텍스트")
                .evidenceAttachmentUuid(null)
                .checkedAt(Instant.now())
                .build();
    }

    private CompanySafetyProfile stubProfile(long id, long companyId) {
        return CompanySafetyProfile.builder().id(id).companyId(companyId).riskGrade("D").build();
    }

    // ──────────────────────────────────────────────
    // getChecklistByReport — 권한 분기
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("체크리스트 조회 — 관리자는 권한 검사 없이 결과 반환")
    void getChecklistByReport_admin_skipsAccessCheck() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 100L);
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(report));
        List<CheckResultResponse> stubResponses = List.of(
                new CheckResultResponse(11L, "PPE", "안전모", "CRITICAL", "DONE", null, null, 50L, Instant.now())
        );
        when(checkResultMapper.findChecklistWithStatusByReportId(1L)).thenReturn(stubResponses);

        // act
        List<CheckResultResponse> result = service.getChecklistByReport(reportUuid, true, 999L);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).itemId()).isEqualTo(11L);
        // 관리자는 profile 조회 없음
        verify(profileMapper, never()).findByCompanyId(any());
    }

    @Test
    @DisplayName("체크리스트 조회 — 본인 프로필 보고서면 비-관리자도 정상 조회")
    void getChecklistByReport_owner_returnsResults() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 100L);
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(report));
        when(profileMapper.findByCompanyId(50L)).thenReturn(Optional.of(stubProfile(100L, 50L)));
        when(checkResultMapper.findChecklistWithStatusByReportId(1L)).thenReturn(List.of());

        // act
        List<CheckResultResponse> result = service.getChecklistByReport(reportUuid, false, 50L);

        // assert
        assertThat(result).isEmpty();
        verify(profileMapper).findByCompanyId(50L);
    }

    @Test
    @DisplayName("체크리스트 조회 — 비-관리자가 타인 보고서 접근 시 AccessDeniedException")
    void getChecklistByReport_otherCompanyReport_throwsAccessDenied() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 100L);
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(report));
        // 요청자의 프로필 ID는 999L (보고서 소유 100L과 다름)
        when(profileMapper.findByCompanyId(999L)).thenReturn(Optional.of(stubProfile(999L, 999L)));

        // act + assert
        assertThatThrownBy(() -> service.getChecklistByReport(reportUuid, false, 999L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("본인 회사 프로필");

        verify(checkResultMapper, never()).findChecklistWithStatusByReportId(any());
    }

    @Test
    @DisplayName("체크리스트 조회 — 존재하지 않는 보고서는 SafetyReportNotFoundException")
    void getChecklistByReport_nonExistentReport_throwsReportNotFound() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getChecklistByReport(reportUuid, true, 100L))
                .isInstanceOf(SafetyReportNotFoundException.class);

        verify(checkResultMapper, never()).findChecklistWithStatusByReportId(any());
    }

    @Test
    @DisplayName("체크리스트 조회 — 비-관리자가 프로필 미존재 시 SafetyProfileNotFoundException")
    void getChecklistByReport_nonAdminMissingProfile_throwsProfileNotFound() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(stubReport(1L, 100L)));
        when(profileMapper.findByCompanyId(50L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getChecklistByReport(reportUuid, false, 50L))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // upsertCheckResult — 정상 + 에러 분기
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("체크 결과 upsert — 관리자 권한으로 정상 저장 후 응답 반환")
    void upsertCheckResult_admin_savesAndReturns() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 100L);
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(report));
        SafetyChecklistItem item = stubItem(11L, "CRITICAL");
        when(itemMapper.findById(11L)).thenReturn(Optional.of(item));

        SafetyCheckResult saved = stubResult(1L, 11L, "DONE");
        when(checkResultMapper.findByReportIdAndItemId(1L, 11L)).thenReturn(Optional.of(saved));

        UUID attachmentUuid = UUID.randomUUID();
        CheckResultRequest req = new CheckResultRequest("DONE", "확인 완료", attachmentUuid);

        // act
        CheckResultResponse result = service.upsertCheckResult(reportUuid, 11L, req, 50L, true, 999L);

        // assert — upsert 호출 검증
        ArgumentCaptor<SafetyCheckResult> captor = ArgumentCaptor.forClass(SafetyCheckResult.class);
        verify(checkResultMapper).upsert(captor.capture());
        SafetyCheckResult upserted = captor.getValue();
        assertThat(upserted.getReportId()).isEqualTo(1L);
        assertThat(upserted.getItemId()).isEqualTo(11L);
        assertThat(upserted.getCheckedBy()).isEqualTo(50L);
        assertThat(upserted.getStatus()).isEqualTo("DONE");
        assertThat(upserted.getEvidenceText()).isEqualTo("확인 완료");
        assertThat(upserted.getEvidenceAttachmentUuid()).isEqualTo(attachmentUuid);

        // 응답 매핑 검증
        assertThat(result.itemId()).isEqualTo(11L);
        assertThat(result.category()).isEqualTo("PPE");
        assertThat(result.itemText()).isEqualTo("안전모 착용 점검");
        assertThat(result.severity()).isEqualTo("CRITICAL");
        assertThat(result.status()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("체크 결과 upsert — saved 결과 미반환 시 upsert 객체 자체로 fallback 응답")
    void upsertCheckResult_savedNotFound_fallsBackToUpsertObject() {
        // arrange — findByReportIdAndItemId가 빈 Optional 반환 → upsert 객체 사용
        UUID reportUuid = UUID.randomUUID();
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(stubReport(1L, 100L)));
        when(itemMapper.findById(11L)).thenReturn(Optional.of(stubItem(11L, "HIGH")));
        when(checkResultMapper.findByReportIdAndItemId(1L, 11L)).thenReturn(Optional.empty());

        CheckResultRequest req = new CheckResultRequest("IN_PROGRESS", "진행 중", null);

        // act
        CheckResultResponse result = service.upsertCheckResult(reportUuid, 11L, req, 50L, true, 999L);

        // assert — fallback 시 saved.checkedAt이 null이므로 응답 checkedAt이 null
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.evidenceText()).isEqualTo("진행 중");
        assertThat(result.checkedBy()).isEqualTo(50L);
    }

    @Test
    @DisplayName("체크 결과 upsert — 보고서 미존재 시 SafetyReportNotFoundException")
    void upsertCheckResult_nonExistentReport_throwsReportNotFound() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.empty());

        CheckResultRequest req = new CheckResultRequest("DONE", "텍스트", null);

        // act + assert
        assertThatThrownBy(() -> service.upsertCheckResult(reportUuid, 11L, req, 50L, true, 100L))
                .isInstanceOf(SafetyReportNotFoundException.class);

        verify(checkResultMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("체크 결과 upsert — 항목 미존재 시 SafetyChecklistItemNotFoundException")
    void upsertCheckResult_nonExistentItem_throwsItemNotFound() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(stubReport(1L, 100L)));
        when(itemMapper.findById(99L)).thenReturn(Optional.empty());

        CheckResultRequest req = new CheckResultRequest("DONE", "텍스트", null);

        // act + assert
        assertThatThrownBy(() -> service.upsertCheckResult(reportUuid, 99L, req, 50L, true, 100L))
                .isInstanceOf(SafetyChecklistItemNotFoundException.class);

        verify(checkResultMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("체크 결과 upsert — 비-관리자가 타인 보고서 수정 시 AccessDeniedException")
    void upsertCheckResult_otherCompanyReport_throwsAccessDenied() {
        // arrange
        UUID reportUuid = UUID.randomUUID();
        SafetyGuidelineReport report = stubReport(1L, 100L);
        when(reportMapper.findByUuid(reportUuid)).thenReturn(Optional.of(report));
        when(profileMapper.findByCompanyId(999L)).thenReturn(Optional.of(stubProfile(999L, 999L)));

        CheckResultRequest req = new CheckResultRequest("DONE", "텍스트", null);

        // act + assert
        assertThatThrownBy(() -> service.upsertCheckResult(reportUuid, 11L, req, 50L, false, 999L))
                .isInstanceOf(AccessDeniedException.class);

        verify(itemMapper, never()).findById(any());
        verify(checkResultMapper, never()).upsert(any());
    }

    // ──────────────────────────────────────────────
    // getOverallStats
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("통계 조회 — totalResults>0이면 completionRate=done/totalResults 계산")
    void getOverallStats_withResults_calculatesCompletionRate() {
        // arrange
        when(checkResultMapper.countDoneAcrossAll()).thenReturn(40L);
        when(checkResultMapper.countInProgressAcrossAll()).thenReturn(20L);
        when(checkResultMapper.countBlockedAcrossAll()).thenReturn(10L);
        when(checkResultMapper.countNaAcrossAll()).thenReturn(30L);
        when(checkResultMapper.countTotalReports()).thenReturn(5L);
        when(checkResultMapper.countTotalChecklistItems()).thenReturn(50L);
        when(checkResultMapper.countTotalCheckResults()).thenReturn(100L);

        // act
        ChecklistStatsResponse result = service.getOverallStats();

        // assert
        assertThat(result.totalReports()).isEqualTo(5L);
        assertThat(result.totalItems()).isEqualTo(50L);
        assertThat(result.doneCount()).isEqualTo(40L);
        assertThat(result.inProgressCount()).isEqualTo(20L);
        assertThat(result.blockedCount()).isEqualTo(10L);
        assertThat(result.naCount()).isEqualTo(30L);
        assertThat(result.completionRate()).isEqualTo(0.4); // 40/100
    }

    @Test
    @DisplayName("통계 조회 — totalResults=0이면 completionRate=0.0 (0 나눗셈 방어)")
    void getOverallStats_zeroTotalResults_completionRateIsZero() {
        // arrange
        when(checkResultMapper.countDoneAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countInProgressAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countBlockedAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countNaAcrossAll()).thenReturn(0L);
        when(checkResultMapper.countTotalReports()).thenReturn(0L);
        when(checkResultMapper.countTotalChecklistItems()).thenReturn(0L);
        when(checkResultMapper.countTotalCheckResults()).thenReturn(0L);

        // act
        ChecklistStatsResponse result = service.getOverallStats();

        // assert
        assertThat(result.completionRate()).isEqualTo(0.0);
        assertThat(result.totalReports()).isZero();
        assertThat(result.totalItems()).isZero();
    }

    @Test
    @DisplayName("통계 조회 — 부분 완료 비율 (3/7) 계산")
    void getOverallStats_partialCompletion_calculatesFractional() {
        // arrange
        when(checkResultMapper.countDoneAcrossAll()).thenReturn(3L);
        when(checkResultMapper.countInProgressAcrossAll()).thenReturn(2L);
        when(checkResultMapper.countBlockedAcrossAll()).thenReturn(1L);
        when(checkResultMapper.countNaAcrossAll()).thenReturn(1L);
        when(checkResultMapper.countTotalReports()).thenReturn(2L);
        when(checkResultMapper.countTotalChecklistItems()).thenReturn(10L);
        when(checkResultMapper.countTotalCheckResults()).thenReturn(7L);

        // act
        ChecklistStatsResponse result = service.getOverallStats();

        // assert — 3/7 ≈ 0.4286
        assertThat(result.completionRate()).isCloseTo(3.0 / 7.0, within(0.0001));
    }
}
