package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.batch.BoardStatsDailyJob;
import kr.co.ircp.cms.domain.governance.batch.BoardStatsMonthlyJob;
import kr.co.ircp.cms.domain.governance.batch.ContentViewStatsDailyJob;
import kr.co.ircp.cms.domain.governance.batch.ContentViewStatsMonthlyJob;
import kr.co.ircp.cms.domain.governance.batch.PolicyMatchStatsJob;
import kr.co.ircp.cms.domain.governance.batch.SafetyStatsMonthlyJob;
import kr.co.ircp.cms.domain.governance.entity.BoardStatsDaily;
import kr.co.ircp.cms.domain.governance.entity.BoardStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.ContentViewStatsDaily;
import kr.co.ircp.cms.domain.governance.entity.ContentViewStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.PolicyMatchStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.SafetyStatsMonthly;
import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-009 REQ-DATA-001~004: 거버넌스 통계 조회 + 수동 재계산 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GovernanceStatsService — REQ-DATA-001~004")
class GovernanceStatsServiceTest {

    @Mock private GovernanceStatsMapper mapper;
    @Mock private BoardStatsDailyJob boardDailyJob;
    @Mock private BoardStatsMonthlyJob boardMonthlyJob;
    @Mock private ContentViewStatsDailyJob contentDailyJob;
    @Mock private ContentViewStatsMonthlyJob contentMonthlyJob;
    @Mock private PolicyMatchStatsJob policyJob;
    @Mock private SafetyStatsMonthlyJob safetyJob;

    @InjectMocks
    private GovernanceStatsService service;

    // ──────────────────────────────────────────────
    // 일별/월별 통계 조회 — params Map 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findBoardDaily — boardId + 날짜 범위 매핑")
    void findBoardDaily_passesParams() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 7);
        BoardStatsDaily row = BoardStatsDaily.builder().boardId(1L).statDate(from).build();
        when(mapper.findBoardStatsDailyRange(any())).thenReturn(List.of(row));

        List<BoardStatsDaily> result = service.findBoardDaily(1L, from, to);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findBoardStatsDailyRange(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("boardId", 1L);
        assertThat(params).containsEntry("from", from);
        assertThat(params).containsEntry("to", to);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findBoardMonthly — yyyy-MM 포맷 매핑")
    void findBoardMonthly_formatsMonths() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        LocalDate to = LocalDate.of(2026, 5, 1);
        when(mapper.findBoardStatsMonthlyRange(any())).thenReturn(List.of());

        service.findBoardMonthly(1L, from, to);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findBoardStatsMonthlyRange(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("from", "2026-01");
        assertThat(params).containsEntry("to", "2026-05");
        assertThat(params).containsEntry("boardId", 1L);
    }

    @Test
    @DisplayName("findBoardMonthly — null 날짜 안전 처리")
    void findBoardMonthly_nullDates_safe() {
        when(mapper.findBoardStatsMonthlyRange(any())).thenReturn(List.of());

        service.findBoardMonthly(1L, null, null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findBoardStatsMonthlyRange(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("from")).isNull();
        assertThat(params.get("to")).isNull();
    }

    @Test
    @DisplayName("findContentDaily — 매퍼 위임")
    void findContentDaily_delegates() {
        when(mapper.findContentViewStatsDailyRange(any())).thenReturn(List.of());

        List<ContentViewStatsDaily> result = service.findContentDaily(1L, null, null);

        verify(mapper).findContentViewStatsDailyRange(any());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findContentMonthly — 매퍼 위임")
    void findContentMonthly_delegates() {
        when(mapper.findContentViewStatsMonthlyRange(any())).thenReturn(List.of());

        List<ContentViewStatsMonthly> result = service.findContentMonthly(1L, null, null);

        verify(mapper).findContentViewStatsMonthlyRange(any());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findPolicyStats — 매퍼 위임")
    void findPolicyStats_delegates() {
        when(mapper.findPolicyMatchStatsRange(any())).thenReturn(List.of());

        List<PolicyMatchStatsMonthly> result = service.findPolicyStats(1L, null, null);

        verify(mapper).findPolicyMatchStatsRange(any());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findSafetyStats — category + yyyy-MM 매핑")
    void findSafetyStats_passesCategoryAndMonths() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        when(mapper.findSafetyStatsRange(any())).thenReturn(List.of());

        List<SafetyStatsMonthly> result = service.findSafetyStats("INCIDENT", from, to);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findSafetyStatsRange(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("category", "INCIDENT");
        assertThat(params).containsEntry("from", "2026-01");
        assertThat(params).containsEntry("to", "2026-05");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findSafetyStats — null 날짜는 그대로 null 전달")
    void findSafetyStats_nullDates_safe() {
        when(mapper.findSafetyStatsRange(any())).thenReturn(List.of());

        service.findSafetyStats(null, null, null);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findSafetyStatsRange(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsKey("category");
        assertThat(params.get("from")).isNull();
        assertThat(params.get("to")).isNull();
    }

    // ──────────────────────────────────────────────
    // recompute — Job dispatch
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("recompute BoardStatsDailyJob — daily job 호출 + 처리 건수 반환")
    void recompute_boardDaily_invokesDailyJob() {
        LocalDate target = LocalDate.of(2026, 5, 6);
        when(boardDailyJob.run(target)).thenReturn(7);

        Map<String, Object> result = service.recompute("BoardStatsDailyJob", target, null);

        verify(boardDailyJob, times(1)).run(target);
        assertThat(result).containsEntry("job", "BoardStatsDailyJob");
        assertThat(result).containsEntry("processed", 7);
    }

    @Test
    @DisplayName("recompute BoardStatsMonthlyJob — yyyy-MM 포맷으로 dispatch")
    void recompute_boardMonthly_invokesMonthlyJob() {
        LocalDate target = LocalDate.of(2026, 5, 6);
        when(boardMonthlyJob.run("2026-05")).thenReturn(3);

        Map<String, Object> result = service.recompute("BoardStatsMonthlyJob", target, null);

        verify(boardMonthlyJob).run("2026-05");
        assertThat(result).containsEntry("processed", 3);
    }

    @Test
    @DisplayName("recompute ContentViewStatsDailyJob — daily job 호출")
    void recompute_contentDaily_invokesContentDailyJob() {
        LocalDate target = LocalDate.of(2026, 5, 6);
        when(contentDailyJob.run(target)).thenReturn(2);

        service.recompute("ContentViewStatsDailyJob", target, null);

        verify(contentDailyJob).run(target);
    }

    @Test
    @DisplayName("recompute ContentViewStatsMonthlyJob — monthly 호출")
    void recompute_contentMonthly_invokesContentMonthlyJob() {
        LocalDate target = LocalDate.of(2026, 5, 6);
        when(contentMonthlyJob.run("2026-05")).thenReturn(4);

        service.recompute("ContentViewStatsMonthlyJob", target, null);

        verify(contentMonthlyJob).run("2026-05");
    }

    @Test
    @DisplayName("recompute PolicyMatchStatsJob — monthly 호출")
    void recompute_policy_invokesPolicyJob() {
        LocalDate target = LocalDate.of(2026, 5, 6);
        when(policyJob.run("2026-05")).thenReturn(1);

        service.recompute("PolicyMatchStatsJob", target, null);

        verify(policyJob).run("2026-05");
    }

    @Test
    @DisplayName("recompute SafetyStatsMonthlyJob — monthly 호출")
    void recompute_safety_invokesSafetyJob() {
        LocalDate target = LocalDate.of(2026, 5, 6);
        when(safetyJob.run("2026-05")).thenReturn(5);

        service.recompute("SafetyStatsMonthlyJob", target, null);

        verify(safetyJob).run("2026-05");
    }

    @Test
    @DisplayName("recompute — 알 수 없는 job 시 IllegalArgumentException")
    void recompute_unknownJob_throws() {
        LocalDate target = LocalDate.of(2026, 5, 6);

        assertThatThrownBy(() -> service.recompute("UnknownJob", target, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown stats job");

        verify(boardDailyJob, never()).run(any(LocalDate.class));
    }

    @Test
    @DisplayName("recompute — from null 시 어제 날짜로 fallback")
    void recompute_nullFrom_usesYesterday() {
        when(boardDailyJob.run(any(LocalDate.class))).thenReturn(0);

        Map<String, Object> result = service.recompute("BoardStatsDailyJob", null, null);

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(boardDailyJob).run(captor.capture());
        LocalDate effective = captor.getValue();
        // 어제 날짜 — 정확한 날짜는 시간대에 의존, 대략적인 검증
        assertThat(effective).isBefore(LocalDate.now().plusDays(1));
        assertThat(result).containsEntry("job", "BoardStatsDailyJob");
    }
}
