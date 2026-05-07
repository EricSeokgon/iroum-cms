package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.entity.RecoveryDrillLog;
import kr.co.ircp.cms.domain.governance.repository.RecoveryDrillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecoveryDrillService GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011~012 — DAR-009 RTO 240분 / RPO 60분 복구 훈련 이력 관리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecoveryDrillService GREEN 테스트 (REQ-GOV-011, REQ-GOV-012)")
class RecoveryDrillServiceTest {

    @Mock
    private RecoveryDrillMapper mapper;

    private RecoveryDrillService service;

    @BeforeEach
    void setUp() {
        service = new RecoveryDrillService(mapper);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private RecoveryDrillLog stubDrill(long id, String type, String result, int year) {
        return RecoveryDrillLog.builder()
                .id(id)
                .drillDate(LocalDate.of(year, 6, 15))
                .drillType(type)
                .result(result)
                .rtoActualMin(180)
                .rpoActualMin(45)
                .rtoTargetMin(240)
                .rpoTargetMin(60)
                .performedBy(99L)
                .checklistJson("{\"items\":[]}")
                .notes("훈련 " + id)
                .build();
    }

    // ──────────────────────────────────────────────
    // findById
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findById — 존재하는 ID는 mapper.findById 결과 반환")
    void findById_existingId_returnsDrill() {
        RecoveryDrillLog drill = stubDrill(1L, "BACKUP_RESTORE", "PASS", 2026);
        when(mapper.findById(1L)).thenReturn(Optional.of(drill));

        Optional<RecoveryDrillLog> result = service.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getDrillType()).isEqualTo("BACKUP_RESTORE");
        assertThat(result.get().getResult()).isEqualTo("PASS");
        verify(mapper).findById(1L);
    }

    @Test
    @DisplayName("findById — 존재하지 않는 ID는 Optional.empty 반환")
    void findById_nonExistentId_returnsEmpty() {
        when(mapper.findById(999L)).thenReturn(Optional.empty());

        Optional<RecoveryDrillLog> result = service.findById(999L);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // findFiltered
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findFiltered — drillType + result + year를 params Map에 매핑")
    void findFiltered_passesAllParams() {
        when(mapper.findFiltered(any())).thenReturn(List.of(stubDrill(1L, "FAILOVER", "PASS", 2026)));

        List<RecoveryDrillLog> result = service.findFiltered("FAILOVER", "PASS", 2026);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findFiltered(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("drillType", "FAILOVER");
        assertThat(params).containsEntry("result", "PASS");
        assertThat(params).containsEntry("year", 2026);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findFiltered — null 인자는 그대로 Map에 매핑")
    void findFiltered_nullArgs_safe() {
        when(mapper.findFiltered(any())).thenReturn(List.of());

        service.findFiltered(null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findFiltered(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsKeys("drillType", "result", "year");
        assertThat(params.get("drillType")).isNull();
        assertThat(params.get("result")).isNull();
        assertThat(params.get("year")).isNull();
    }

    @Test
    @DisplayName("findFiltered — 빈 결과는 빈 리스트 반환")
    void findFiltered_emptyResult_returnsEmptyList() {
        when(mapper.findFiltered(any())).thenReturn(List.of());

        List<RecoveryDrillLog> result = service.findFiltered("PITR", "FAIL", 2025);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findFiltered — 다중 결과 정상 반환")
    void findFiltered_multipleResults_returnsAll() {
        when(mapper.findFiltered(any())).thenReturn(List.of(
                stubDrill(1L, "BACKUP_RESTORE", "PASS", 2026),
                stubDrill(2L, "FAILOVER", "PARTIAL", 2026),
                stubDrill(3L, "PITR", "FAIL", 2026)
        ));

        List<RecoveryDrillLog> result = service.findFiltered(null, null, 2026);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(RecoveryDrillLog::getResult)
                .containsExactly("PASS", "PARTIAL", "FAIL");
    }

    // ──────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("create — mapper.insert 호출 후 동일 객체 반환")
    void create_callsInsertAndReturnsSameInstance() {
        RecoveryDrillLog newLog = stubDrill(0L, "BACKUP_RESTORE", "PASS", 2026);

        RecoveryDrillLog result = service.create(newLog);

        ArgumentCaptor<RecoveryDrillLog> captor = ArgumentCaptor.forClass(RecoveryDrillLog.class);
        verify(mapper).insert(captor.capture());
        RecoveryDrillLog inserted = captor.getValue();
        assertThat(inserted.getDrillType()).isEqualTo("BACKUP_RESTORE");
        assertThat(inserted.getResult()).isEqualTo("PASS");
        assertThat(inserted.getRtoTargetMin()).isEqualTo(240);
        assertThat(inserted.getRpoTargetMin()).isEqualTo(60);
        assertThat(result).isSameAs(newLog);
    }

    @Test
    @DisplayName("create — RTO 미달성 (actual > target)도 그대로 INSERT (검증은 호출자 책임)")
    void create_rtoExceedsTarget_stillInserts() {
        RecoveryDrillLog rtoOver = RecoveryDrillLog.builder()
                .drillDate(LocalDate.of(2026, 5, 1))
                .drillType("FAILOVER")
                .result("FAIL")
                .rtoActualMin(360) // 240 초과
                .rpoActualMin(120) // 60 초과
                .rtoTargetMin(240)
                .rpoTargetMin(60)
                .build();

        RecoveryDrillLog result = service.create(rtoOver);

        verify(mapper).insert(rtoOver);
        assertThat(result.getResult()).isEqualTo("FAIL");
        assertThat(result.getRtoActualMin()).isEqualTo(360);
    }
}
