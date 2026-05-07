package kr.co.ircp.cms.domain.system.maintenance.service;

import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceRequest;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceResponse;
import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import kr.co.ircp.cms.domain.system.maintenance.mapper.MaintenanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MaintenanceService GREEN 테스트.
 * REQ-SYSTEM-005-D: 점검 활성화 + 자동 완료 + 현재 활성 점검 조회
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceService GREEN 테스트 (REQ-SYSTEM-005-D)")
class MaintenanceServiceTest {

    @Mock private MaintenanceMapper maintenanceMapper;

    private MaintenanceServiceImpl maintenanceService;

    @BeforeEach
    void setUp() {
        maintenanceService = new MaintenanceServiceImpl(maintenanceMapper);
    }

    private Maintenance sampleMaintenance(Long id, String status) {
        return Maintenance.builder()
                .id(id)
                .title("점검 공지")
                .messageKo("점검 중입니다")
                .messageEn("Under maintenance")
                .startAt(Instant.now().minusSeconds(60))
                .endAt(Instant.now().plusSeconds(3600))
                .status(status)
                .allowAdminAccess(true)
                .build();
    }

    // ──────────────────────────────────────────────
    // create()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create() — 정상 생성 후 가장 마지막 매칭 항목 반환")
    void create_returnsLastMatchingItem() {
        // given
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        MaintenanceRequest req = new MaintenanceRequest(
                "점검 공지", "점검 중", "Under maintenance", start, end, true);

        Maintenance other = Maintenance.builder().id(1L).title("이전 공지").status("COMPLETED").build();
        Maintenance latest = Maintenance.builder()
                .id(2L).title("점검 공지").status("SCHEDULED")
                .startAt(start).endAt(end).allowAdminAccess(true)
                .build();
        when(maintenanceMapper.findAll()).thenReturn(List.of(other, latest));

        // when
        MaintenanceResponse result = maintenanceService.create(req);

        // then
        verify(maintenanceMapper).insert(any());
        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.title()).isEqualTo("점검 공지");
    }

    @Test
    @DisplayName("create() — allowAdminAccess null 시 true 기본값으로 INSERT")
    void create_nullAllowAdminAccess_defaultsTrue() {
        // given
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        MaintenanceRequest req = new MaintenanceRequest(
                "점검 공지", "ko", "en", start, end, null);
        Maintenance inserted = Maintenance.builder()
                .id(1L).title("점검 공지").status("SCHEDULED")
                .startAt(start).endAt(end).allowAdminAccess(true).build();
        when(maintenanceMapper.findAll()).thenReturn(List.of(inserted));

        // when
        maintenanceService.create(req);

        // then
        ArgumentCaptor<Maintenance> captor = ArgumentCaptor.forClass(Maintenance.class);
        verify(maintenanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getAllowAdminAccess()).isTrue();
        assertThat(captor.getValue().getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("create() — allowAdminAccess false 시 그대로 적용")
    void create_explicitFalseAllowAdminAccess() {
        // given
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        MaintenanceRequest req = new MaintenanceRequest(
                "긴급점검", null, null, start, end, false);
        Maintenance inserted = Maintenance.builder()
                .id(1L).title("긴급점검").status("SCHEDULED")
                .startAt(start).endAt(end).allowAdminAccess(false).build();
        when(maintenanceMapper.findAll()).thenReturn(List.of(inserted));

        // when
        maintenanceService.create(req);

        // then
        ArgumentCaptor<Maintenance> captor = ArgumentCaptor.forClass(Maintenance.class);
        verify(maintenanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getAllowAdminAccess()).isFalse();
    }

    // ──────────────────────────────────────────────
    // getById()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getById() — 존재하면 MaintenanceResponse 반환")
    void getById_returns_response() {
        // given
        when(maintenanceMapper.findById(1L)).thenReturn(Optional.of(sampleMaintenance(1L, "ACTIVE")));

        // when
        MaintenanceResponse result = maintenanceService.getById(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getById() — 존재하지 않으면 NoSuchElementException")
    void getById_throws_when_not_found() {
        // given
        when(maintenanceMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> maintenanceService.getById(99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ──────────────────────────────────────────────
    // listAll()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("listAll() — 전체 점검 목록 반환")
    void listAll_returnsAll() {
        // given
        when(maintenanceMapper.findAll()).thenReturn(List.of(
                sampleMaintenance(1L, "SCHEDULED"),
                sampleMaintenance(2L, "ACTIVE")));

        // when
        List<MaintenanceResponse> result = maintenanceService.listAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listAll() — 빈 결과")
    void listAll_empty() {
        // given
        when(maintenanceMapper.findAll()).thenReturn(List.of());

        // when
        List<MaintenanceResponse> result = maintenanceService.listAll();

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // activate()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("activate() — 존재하지 않는 id면 NoSuchElementException")
    void activate_throws_when_not_found() {
        // given
        when(maintenanceMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> maintenanceService.activate(99L))
                .isInstanceOf(NoSuchElementException.class);
        verify(maintenanceMapper, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("activate() — ACTIVE 상태로 updateStatus 호출")
    void activate_updates_status_to_active() {
        // given
        Maintenance m = sampleMaintenance(1L, "SCHEDULED");
        when(maintenanceMapper.findById(1L)).thenReturn(Optional.of(m))
                .thenReturn(Optional.of(sampleMaintenance(1L, "ACTIVE")));

        // when
        MaintenanceResponse result = maintenanceService.activate(1L);

        // then
        verify(maintenanceMapper).updateStatus(1L, "ACTIVE");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    // ──────────────────────────────────────────────
    // completeExpired() (Scheduled)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("completeExpired() — 자동 완료 건수가 0이어도 정상 호출")
    void completeExpired_zeroCount() {
        // given
        when(maintenanceMapper.completeExpired()).thenReturn(0);

        // when
        maintenanceService.completeExpired();

        // then
        verify(maintenanceMapper).completeExpired();
    }

    @Test
    @DisplayName("completeExpired() — 다수 건 완료 시 mapper 호출")
    void completeExpired_multipleCount() {
        // given
        when(maintenanceMapper.completeExpired()).thenReturn(5);

        // when
        maintenanceService.completeExpired();

        // then
        verify(maintenanceMapper).completeExpired();
    }

    // ──────────────────────────────────────────────
    // findActive()
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findActive() — 활성 점검 존재 시 Optional 반환")
    void findActive_returnsActive() {
        // given
        Maintenance active = sampleMaintenance(1L, "ACTIVE");
        when(maintenanceMapper.findActive()).thenReturn(Optional.of(active));

        // when
        Optional<Maintenance> result = maintenanceService.findActive();

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("findActive() — 활성 점검 미존재 시 빈 Optional")
    void findActive_empty() {
        // given
        when(maintenanceMapper.findActive()).thenReturn(Optional.empty());

        // when
        Optional<Maintenance> result = maintenanceService.findActive();

        // then
        assertThat(result).isEmpty();
    }
}
