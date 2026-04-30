package kr.co.ircp.cms.domain.system.maintenance.service;

import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceResponse;
import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import kr.co.ircp.cms.domain.system.maintenance.mapper.MaintenanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("activate() — 존재하지 않는 id면 NoSuchElementException")
    void activate_throws_when_not_found() {
        // given
        when(maintenanceMapper.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> maintenanceService.activate(99L))
                .isInstanceOf(NoSuchElementException.class);
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

    @Test
    @DisplayName("completeExpired() — mapper.completeExpired 호출")
    void completeExpired_calls_mapper() {
        // given
        when(maintenanceMapper.completeExpired()).thenReturn(2);

        // when
        maintenanceService.completeExpired();

        // then
        verify(maintenanceMapper).completeExpired();
    }
}
