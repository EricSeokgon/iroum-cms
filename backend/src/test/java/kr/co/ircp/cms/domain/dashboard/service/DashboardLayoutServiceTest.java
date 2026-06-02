package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.LayoutRequest;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutResponse;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayout;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayoutWidget;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException;
import kr.co.ircp.cms.domain.dashboard.preference.repository.UserDashboardPreferenceMapper;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardLayoutMapper;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardWidgetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardLayoutService 단위 테스트.
 * REQ-VIZ-002 (CRUD/공유/복제/기본)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardLayoutService — 레이아웃 CRUD + 복제 + one-default (REQ-VIZ-002)")
class DashboardLayoutServiceTest {

    @Mock private DashboardLayoutMapper layoutMapper;
    @Mock private DashboardWidgetMapper widgetMapper;
    @Mock private UserDashboardPreferenceMapper userDashboardPreferenceMapper;

    private DashboardLayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardLayoutServiceImpl(layoutMapper, widgetMapper, userDashboardPreferenceMapper);
    }

    private DashboardLayout sampleLayout(Long id, Long ownerId, String name) {
        return DashboardLayout.builder()
                .id(id).ownerId(ownerId).name(name)
                .isDefault(false)
                .gridConfig("{\"columns\":12}")
                .sharedWith(List.of())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    private DashboardLayoutWidget mapping(Long layoutId, Long widgetId, String instanceId) {
        return DashboardLayoutWidget.builder()
                .layoutId(layoutId).widgetId(widgetId).instanceId(instanceId)
                .position("{\"x\":0,\"y\":0,\"w\":6,\"h\":4}")
                .configOverride("{}")
                .sortOrder(0).build();
    }

    @Test
    @DisplayName("create — layout + widgets 동시 등록")
    void create_layoutWithWidgets() {
        LayoutRequest.LayoutWidgetEntry w = new LayoutRequest.LayoutWidgetEntry(
                10L, "inst-a", "{\"x\":0,\"y\":0,\"w\":6,\"h\":4}", "{}", 0);
        LayoutRequest req = new LayoutRequest("My Dash", "desc",
                "{\"columns\":12}", List.of(), List.of(w));

        // Simulate insert assigning id
        Answer<Void> assignId = (InvocationOnMock inv) -> {
            DashboardLayout l = inv.getArgument(0);
            l.setId(99L);
            return null;
        };
        org.mockito.Mockito.doAnswer(assignId).when(layoutMapper).insertLayout(any());

        LayoutResponse resp = service.create(1L, req);

        assertThat(resp.id()).isEqualTo(99L);
        verify(layoutMapper, times(1)).insertLayout(any());
        verify(layoutMapper, times(1)).insertWidget(any());
    }

    @Test
    @DisplayName("getById — 미존재 시 DashboardLayoutNotFoundException")
    void getById_notFound() {
        when(layoutMapper.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(DashboardLayoutNotFoundException.class);
    }

    @Test
    @DisplayName("getById — widgets 와 함께 응답 반환")
    void getById_returnsLayoutWithWidgets() {
        DashboardLayout l = sampleLayout(5L, 1L, "Main");
        when(layoutMapper.findById(5L)).thenReturn(Optional.of(l));
        when(layoutMapper.findWidgetsByLayoutId(5L))
                .thenReturn(List.of(mapping(5L, 10L, "a"), mapping(5L, 11L, "b")));

        LayoutResponse resp = service.getById(5L);

        assertThat(resp.widgets()).hasSize(2);
        assertThat(resp.widgets()).extracting("widgetId").containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("listForUser — 본인 + 공유 레이아웃 통합 조회")
    void listForUser_returnsOwnAndShared() {
        when(layoutMapper.findByOwnerOrShared(eq(1L), anyList()))
                .thenReturn(List.of(sampleLayout(5L, 1L, "Mine"),
                        sampleLayout(7L, 99L, "Shared")));

        List<LayoutResponse> list = service.listForUser(1L, List.of("DEPT_ADMIN"));

        assertThat(list).hasSize(2);
    }

    // ──────────────────────────────────────────────
    // 복제 (REQ-VIZ-002-D-5)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("clone — dashboard_layout + widgets 모두 deep copy, 새 owner_id 적용")
    void clone_deepCopiesLayoutAndWidgets() {
        DashboardLayout source = sampleLayout(5L, 99L, "Original");
        when(layoutMapper.findById(5L)).thenReturn(Optional.of(source));
        when(layoutMapper.findWidgetsByLayoutId(5L))
                .thenReturn(List.of(mapping(5L, 10L, "a"), mapping(5L, 11L, "b")));
        Answer<Void> assignId = (InvocationOnMock inv) -> {
            DashboardLayout l = inv.getArgument(0);
            l.setId(123L);
            return null;
        };
        org.mockito.Mockito.doAnswer(assignId).when(layoutMapper).insertLayout(any());

        LayoutResponse cloned = service.clone(5L, 1L);

        assertThat(cloned.id()).isEqualTo(123L);
        ArgumentCaptor<DashboardLayout> captor = ArgumentCaptor.forClass(DashboardLayout.class);
        verify(layoutMapper).insertLayout(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(1L);
        assertThat(captor.getValue().isDefault()).isFalse();

        // 두 위젯 모두 새 layout id (123) 로 deep copy
        ArgumentCaptor<DashboardLayoutWidget> widgetCap = ArgumentCaptor.forClass(DashboardLayoutWidget.class);
        verify(layoutMapper, times(2)).insertWidget(widgetCap.capture());
        assertThat(widgetCap.getAllValues()).extracting("layoutId").containsOnly(123L);
    }

    @Test
    @DisplayName("clone — 원본 미존재 시 DashboardLayoutNotFoundException")
    void clone_sourceMissing_throws() {
        when(layoutMapper.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.clone(99L, 1L))
                .isInstanceOf(DashboardLayoutNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // one-default (REQ-VIZ-002-D-4)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("setDefault — 기존 default 해제 후 새 default 지정")
    void setDefault_clearsPreviousAndSets() {
        DashboardLayout l = sampleLayout(5L, 1L, "Main");
        when(layoutMapper.findById(5L)).thenReturn(Optional.of(l));

        service.setDefault(5L, 1L);

        verify(layoutMapper, times(1)).clearDefaultForOwner(1L);
        verify(layoutMapper, times(1)).setDefault(5L, true);
    }

    @Test
    @DisplayName("setDefault — 본인 소유가 아니면 권한 거부")
    void setDefault_notOwner_throws() {
        DashboardLayout l = sampleLayout(5L, 99L, "Other");
        when(layoutMapper.findById(5L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> service.setDefault(5L, 1L))
                .isInstanceOf(SecurityException.class);
    }

    // ──────────────────────────────────────────────
    // 공유 레이아웃 읽기 전용 (REQ-VIZ-002-D-3)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("update — 본인 소유가 아니면 SecurityException (공유 레이아웃은 읽기 전용)")
    void update_notOwner_throws() {
        DashboardLayout l = sampleLayout(5L, 99L, "Other");
        when(layoutMapper.findById(5L)).thenReturn(Optional.of(l));

        LayoutRequest req = new LayoutRequest("New", "d", "{}", List.of(), List.of());
        assertThatThrownBy(() -> service.update(5L, 1L, req))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("delete — 본인 소유 레이아웃 정상 삭제")
    void delete_ownerOk() {
        DashboardLayout l = sampleLayout(5L, 1L, "Mine");
        when(layoutMapper.findById(5L)).thenReturn(Optional.of(l));
        when(layoutMapper.deleteLayout(5L)).thenReturn(1);

        service.delete(5L, 1L);

        verify(layoutMapper).deleteLayout(5L);
    }
}
