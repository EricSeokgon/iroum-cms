package kr.co.ircp.cms.domain.dashboard.preference.service;

import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceResponse;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PreferenceUpdateRequest;
import kr.co.ircp.cms.domain.dashboard.preference.entity.UserDashboardPreference;
import kr.co.ircp.cms.domain.dashboard.preference.exception.PreferenceConflictException;
import kr.co.ircp.cms.domain.dashboard.preference.repository.UserDashboardPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — UserDashboardPreferenceService 단위 테스트.
 *
 * <p>RED 단계 — 다음 시나리오를 검증한다:
 * <ul>
 *   <li>REQ-DP-002-1/4: GET 시 row 없으면 lazy upsertDefaults → DEFAULT 응답</li>
 *   <li>REQ-DP-002-4: PATCH 부분 갱신 (theme 만 변경, density 보존)</li>
 *   <li>REQ-DP-002-5: reset 시 스타일은 DEFAULT, hidden 은 보존</li>
 *   <li>REQ-DP-001-1/2: 위젯 가시성 토글 (추가/제거)</li>
 *   <li>REQ-DP-003-5: 낙관적 잠금 충돌 시 PreferenceConflictException</li>
 *   <li>REQ-DP-001-4: 레이아웃 삭제 시 hidden 에서 layout_id 키 제거 (cleanupForLayout)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserDashboardPreferenceService — SPEC-CMS-DASHBOARD-PERSONALIZE-001")
class UserDashboardPreferenceServiceTest {

    @Mock
    UserDashboardPreferenceMapper mapper;

    @InjectMocks
    UserDashboardPreferenceServiceImpl service;

    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        // 별도 초기화 없음
    }

    private UserDashboardPreference defaults() {
        UserDashboardPreference p = UserDashboardPreference.defaults(USER_ID);
        p.setCreatedAt(Instant.parse("2026-05-29T00:00:00Z"));
        p.setUpdatedAt(Instant.parse("2026-05-29T00:00:00Z"));
        return p;
    }

    // ── REQ-DP-002 / API GET ─────────────────────────────────────────────────

    @Test
    @DisplayName("AC-DP-API-1: GET 시 row 없으면 lazy upsertDefaults 후 DEFAULT 응답")
    void getOrCreate_lazyUpsert_whenRowMissing() {
        when(mapper.findByUserId(USER_ID))
                .thenReturn(Optional.empty())          // 1차 조회 — 없음
                .thenReturn(Optional.of(defaults()));   // upsert 후 재조회 — 있음

        PreferenceResponse res = service.getOrCreate(USER_ID);

        verify(mapper, times(1)).upsertDefaults(USER_ID);
        assertThat(res.userId()).isEqualTo(USER_ID);
        assertThat(res.theme()).isEqualTo("SYSTEM");
        assertThat(res.density()).isEqualTo("NORMAL");
        assertThat(res.fontScale()).isEqualByComparingTo("1.00");
        assertThat(res.colorPalettePreference()).isEqualTo("DEFAULT");
        assertThat(res.hiddenWidgetInstanceIds()).isEmpty();
    }

    @Test
    @DisplayName("AC-DP-API-1: GET 시 row 존재하면 upsertDefaults 호출하지 않는다 (idempotent)")
    void getOrCreate_skipUpsert_whenRowExists() {
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(defaults()));

        service.getOrCreate(USER_ID);

        verify(mapper, never()).upsertDefaults(any());
    }

    // ── REQ-DP-002-4 PATCH ───────────────────────────────────────────────────

    @Test
    @DisplayName("AC-DP-API-2: PATCH 시 변경된 필드만 갱신, 나머지는 보존")
    void update_partialPatch_appliesOnlyProvidedFields() {
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(defaults()));
        when(mapper.patch(eq(USER_ID), eq("DARK"), any(), any(), any(), any(), any()))
                .thenReturn(1);

        PreferenceUpdateRequest req = new PreferenceUpdateRequest(
                "DARK", null, null, null, null, null);

        service.update(USER_ID, req);

        verify(mapper).patch(
                eq(USER_ID), eq("DARK"), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("REQ-DP-003-5: PATCH 시 낙관적 잠금 충돌이면 PreferenceConflictException 발생")
    void update_throwsConflict_whenOptimisticLockMismatches() {
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(defaults()));
        when(mapper.patch(eq(USER_ID), any(), any(), any(), any(), any(),
                eq(Instant.parse("2020-01-01T00:00:00Z"))))
                .thenReturn(0);   // 0 행 갱신 → 충돌

        PreferenceUpdateRequest req = new PreferenceUpdateRequest(
                "DARK", null, null, null, null,
                Instant.parse("2020-01-01T00:00:00Z"));

        assertThatThrownBy(() -> service.update(USER_ID, req))
                .isInstanceOf(PreferenceConflictException.class);
    }

    // ── REQ-DP-002-5 reset ───────────────────────────────────────────────────

    @Test
    @DisplayName("AC-DP-002-5: reset 시 스타일은 DEFAULT, hidden 은 보존")
    void reset_resetsStyleOnly_keepsHidden() {
        UserDashboardPreference withHidden = defaults();
        withHidden.setHiddenWidgetInstanceIds("{\"1\":[\"w-a\"]}");
        withHidden.setTheme("DARK");
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(withHidden));

        service.reset(USER_ID);

        verify(mapper).resetStyleToDefault(USER_ID);
        verify(mapper, never()).updateHiddenWidgetInstanceIds(any(), any());
    }

    // ── REQ-DP-001-1 / 001-2 위젯 가시성 ──────────────────────────────────────

    @Test
    @DisplayName("AC-DP-001-1: 위젯 숨김 → hidden 배열에 instance_id 추가")
    void toggleVisibility_addsToHidden_whenHiddenTrue() {
        when(mapper.findByUserId(USER_ID))
                .thenReturn(Optional.of(defaults()));   // hidden = "{}"

        service.toggleVisibility(USER_ID, 1L, "w-pv-001", true);

        // 매퍼 호출 JSON 에 새 instance_id 가 포함되어야 함
        verify(mapper).updateHiddenWidgetInstanceIds(eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(json ->
                        json.contains("\"1\"") && json.contains("w-pv-001")));
    }

    @Test
    @DisplayName("AC-DP-001-2: 위젯 표시 → hidden 배열에서 instance_id 제거")
    void toggleVisibility_removesFromHidden_whenHiddenFalse() {
        UserDashboardPreference withHidden = defaults();
        withHidden.setHiddenWidgetInstanceIds("{\"1\":[\"w-pv-001\",\"w-b\"]}");
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(withHidden));

        service.toggleVisibility(USER_ID, 1L, "w-pv-001", false);

        verify(mapper).updateHiddenWidgetInstanceIds(eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(json ->
                        !json.contains("w-pv-001") && json.contains("w-b")));
    }

    @Test
    @DisplayName("AC-DP-001-5: 동일 instance_id 중복 추가는 idempotent")
    void toggleVisibility_idempotent_whenAlreadyHidden() {
        UserDashboardPreference withHidden = defaults();
        withHidden.setHiddenWidgetInstanceIds("{\"1\":[\"w-pv-001\"]}");
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(withHidden));

        service.toggleVisibility(USER_ID, 1L, "w-pv-001", true);

        verify(mapper).updateHiddenWidgetInstanceIds(eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(json -> {
                    int count = json.split("w-pv-001").length - 1;
                    return count == 1;   // 정확히 1번만 등장
                }));
    }

    // ── REQ-DP-001-4 레이아웃 삭제 시 orphan 정리 ────────────────────────────

    @Test
    @DisplayName("AC-DP-001-3: cleanupForLayout — hidden 에서 layout_id 키 제거")
    void cleanupForLayout_removesLayoutKeyFromHidden() {
        UserDashboardPreference withHidden = defaults();
        withHidden.setHiddenWidgetInstanceIds("{\"1\":[\"w-a\"],\"2\":[\"w-b\"]}");
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(withHidden));

        service.cleanupForLayout(USER_ID, 1L);

        verify(mapper).updateHiddenWidgetInstanceIds(eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(json ->
                        !json.contains("\"1\":") && json.contains("\"2\":")));
    }

    @Test
    @DisplayName("hidden 이 비어있는 사용자에 대해 cleanupForLayout 은 호출 무시 (no-op)")
    void cleanupForLayout_noop_whenNoHidden() {
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.cleanupForLayout(USER_ID, 1L);

        verify(mapper, never()).updateHiddenWidgetInstanceIds(any(), any());
    }

    // ── 모든 위젯 표시 (REQ-DP-001-5) ─────────────────────────────────────────

    @Test
    @DisplayName("AC-DP-001-5: showAllWidgets — 특정 layout 의 hidden 배열을 빈 배열로 초기화")
    void showAllWidgets_clearsHiddenForLayout() {
        UserDashboardPreference withHidden = defaults();
        withHidden.setHiddenWidgetInstanceIds("{\"1\":[\"w-a\",\"w-b\"],\"2\":[\"w-c\"]}");
        when(mapper.findByUserId(USER_ID)).thenReturn(Optional.of(withHidden));

        service.showAllWidgets(USER_ID, 1L);

        verify(mapper).updateHiddenWidgetInstanceIds(eq(USER_ID),
                org.mockito.ArgumentMatchers.argThat(json ->
                        json.contains("\"1\":[]") && json.contains("w-c")));
    }
}
