package kr.co.ircp.cms.domain.dashboard.preference.service;

import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayout;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException;
import kr.co.ircp.cms.domain.dashboard.preference.dto.PositionPatchRequest;
import kr.co.ircp.cms.domain.dashboard.preference.exception.PreferenceConflictException;
import kr.co.ircp.cms.domain.dashboard.preference.repository.LayoutPositionMapper;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardLayoutMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — LayoutPositionService 단위 테스트.
 *
 * <p>REQ-DP-003-2: 위젯 위치 일괄 갱신
 * <p>REQ-DP-003-4: 공유받은 레이아웃 편집 금지 (소유자 검증)
 * <p>REQ-DP-003-5 / AC-DP-003-5: 낙관적 잠금 충돌
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LayoutPositionService — SPEC-CMS-DASHBOARD-PERSONALIZE-001")
class LayoutPositionServiceTest {

    @Mock
    DashboardLayoutMapper layoutMapper;

    @Mock
    LayoutPositionMapper positionMapper;

    @InjectMocks
    LayoutPositionServiceImpl service;

    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_ID = 200L;
    private static final Long LAYOUT_ID = 1L;

    private DashboardLayout layout(Long ownerId, Instant updatedAt) {
        DashboardLayout l = DashboardLayout.builder()
                .id(LAYOUT_ID)
                .ownerId(ownerId)
                .name("L1")
                .isDefault(false)
                .gridConfig("{\"columns\":12,\"row_height\":80}")
                .sharedWith(List.of())
                .build();
        l.setUpdatedAt(updatedAt);
        return l;
    }

    @Test
    @DisplayName("REQ-DP-003-2 / AC-DP-003-1: 본인 소유 레이아웃 N개 위치 일괄 갱신 — 각 entry 마다 updatePosition 호출")
    void patchPositions_callsUpdatePosition_perEntry() {
        when(layoutMapper.findById(LAYOUT_ID))
                .thenReturn(Optional.of(layout(OWNER_ID, Instant.now())));

        PositionPatchRequest req = new PositionPatchRequest(
                List.of(
                        new PositionPatchRequest.PositionEntry("w-a",
                                new PositionPatchRequest.Position(0, 0, 6, 4)),
                        new PositionPatchRequest.PositionEntry("w-b",
                                new PositionPatchRequest.Position(6, 0, 6, 4))
                ),
                null);

        when(positionMapper.updatePosition(eq(LAYOUT_ID), any(), any())).thenReturn(1);

        service.patchPositions(LAYOUT_ID, OWNER_ID, req);

        verify(positionMapper, times(2)).updatePosition(eq(LAYOUT_ID), any(), any());
        verify(positionMapper).touchLayoutUpdatedAt(LAYOUT_ID);
    }

    @Test
    @DisplayName("REQ-DP-003-4 / AC-DP-003-3: 다른 소유자 시도 → SecurityException (403)")
    void patchPositions_throwsSecurityException_whenNotOwner() {
        when(layoutMapper.findById(LAYOUT_ID))
                .thenReturn(Optional.of(layout(OWNER_ID, Instant.now())));

        PositionPatchRequest req = new PositionPatchRequest(
                List.of(new PositionPatchRequest.PositionEntry("w-a",
                        new PositionPatchRequest.Position(0, 0, 6, 4))),
                null);

        assertThatThrownBy(() -> service.patchPositions(LAYOUT_ID, OTHER_ID, req))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("미존재 레이아웃 → DashboardLayoutNotFoundException")
    void patchPositions_throwsNotFound_whenLayoutMissing() {
        when(layoutMapper.findById(LAYOUT_ID)).thenReturn(Optional.empty());

        PositionPatchRequest req = new PositionPatchRequest(
                List.of(new PositionPatchRequest.PositionEntry("w-a",
                        new PositionPatchRequest.Position(0, 0, 6, 4))),
                null);

        assertThatThrownBy(() -> service.patchPositions(LAYOUT_ID, OWNER_ID, req))
                .isInstanceOf(DashboardLayoutNotFoundException.class);
    }

    @Test
    @DisplayName("REQ-DP-003-5 / AC-DP-003-5: 낙관적 잠금 — expected_updated_at 불일치 시 PreferenceConflictException")
    void patchPositions_throwsConflict_whenOptimisticLockMismatches() {
        Instant currentDbUpdatedAt = Instant.parse("2026-05-29T10:00:00Z");
        Instant staleExpected = Instant.parse("2026-05-29T09:00:00Z");

        when(layoutMapper.findById(LAYOUT_ID))
                .thenReturn(Optional.of(layout(OWNER_ID, currentDbUpdatedAt)));

        PositionPatchRequest req = new PositionPatchRequest(
                List.of(new PositionPatchRequest.PositionEntry("w-a",
                        new PositionPatchRequest.Position(0, 0, 6, 4))),
                staleExpected);

        assertThatThrownBy(() -> service.patchPositions(LAYOUT_ID, OWNER_ID, req))
                .isInstanceOf(PreferenceConflictException.class);
    }

    @Test
    @DisplayName("REQ-DP-003-3 / AC-DP-003-2: 겹침 검증 — 두 위젯이 동일 좌표면 IllegalArgumentException")
    void patchPositions_throwsOverlap_whenWidgetsOverlap() {
        when(layoutMapper.findById(LAYOUT_ID))
                .thenReturn(Optional.of(layout(OWNER_ID, Instant.now())));

        PositionPatchRequest req = new PositionPatchRequest(
                List.of(
                        new PositionPatchRequest.PositionEntry("w-a",
                                new PositionPatchRequest.Position(0, 0, 6, 4)),
                        new PositionPatchRequest.PositionEntry("w-b",
                                new PositionPatchRequest.Position(3, 2, 6, 4))   // 겹침
                ),
                null);

        assertThatThrownBy(() -> service.patchPositions(LAYOUT_ID, OWNER_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("겹침");
    }
}
