package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.IncidentCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.IncidentDetail;
import kr.co.ircp.cms.domain.safety.dto.IncidentSummary;
import kr.co.ircp.cms.domain.safety.dto.IncidentUpdateRequest;
import kr.co.ircp.cms.domain.safety.dto.SyncResult;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncident;
import kr.co.ircp.cms.domain.safety.exception.SafetyIncidentNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-SAFETY-001 — 사고사례 CRUD + 동기화 mock */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyIncidentService — REQ-SAFETY-001")
class SafetyIncidentServiceTest {

    @Mock private SafetyIncidentMapper incidentMapper;
    private SafetyIncidentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SafetyIncidentServiceImpl(incidentMapper);
    }

    private SafetyIncident sample(long id) {
        return SafetyIncident.builder()
                .id(id).sourceType("MANUAL").industryCode("F4521")
                .incidentType("FALL").severity("FATAL").casualties(1)
                .occurredAt(Instant.now()).status("PUBLISHED").summary("요약")
                .build();
    }

    @Test
    @DisplayName("필터 조회 — 페이징 응답 반환")
    void list_withFilters_returnsPage() {
        when(incidentMapper.findFiltered(eq("F4521"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(sample(1L)));
        when(incidentMapper.countFiltered(eq("F4521"), isNull(), isNull())).thenReturn(1L);

        PageResponse<IncidentSummary> page = service.listIncidents("F4521", null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("단건 조회 — 존재 시 detail 반환")
    void getIncident_exists_returnsDetail() {
        when(incidentMapper.findById(1L)).thenReturn(Optional.of(sample(1L)));

        IncidentDetail detail = service.getIncident(1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.severity()).isEqualTo("FATAL");
    }

    @Test
    @DisplayName("단건 조회 — 미존재 시 SafetyIncidentNotFoundException")
    void getIncident_missing_throwsException() {
        when(incidentMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getIncident(99L))
                .isInstanceOf(SafetyIncidentNotFoundException.class);
    }

    @Test
    @DisplayName("등록 — insert 호출 + detail 반환")
    void create_validRequest_insertsAndReturns() {
        IncidentCreateRequest request = new IncidentCreateRequest(
                "MANUAL", "F4521", null, null, "FALL",
                Instant.now(), "FATAL", 1, "현장A", "요약",
                null, null, null
        );

        IncidentDetail detail = service.createIncident(request);

        assertThat(detail.severity()).isEqualTo("FATAL");
        verify(incidentMapper).insert(any());
    }

    @Test
    @DisplayName("수정 — 존재 사고사례 patch 후 detail 반환")
    void update_existing_returnsUpdatedDetail() {
        SafetyIncident existing = sample(1L);
        when(incidentMapper.findById(1L)).thenReturn(Optional.of(existing));

        IncidentUpdateRequest request = new IncidentUpdateRequest(
                null, null, null, null, null, "SEVERE",
                2, null, "보강 요약", null, null, null
        );

        IncidentDetail detail = service.updateIncident(1L, request);

        assertThat(detail.id()).isEqualTo(1L);
        verify(incidentMapper).update(any());
    }

    @Test
    @DisplayName("논리 삭제 — archiveById 호출")
    void archive_existing_callsArchive() {
        when(incidentMapper.findById(1L)).thenReturn(Optional.of(sample(1L)));

        service.archiveIncident(1L);

        verify(incidentMapper).archiveById(1L);
    }

    @Test
    @DisplayName("외부 동기화 mock — SyncResult 반환")
    void sync_mock_returnsResult() {
        SyncResult result = service.triggerExternalSync("KOSHA_OPENAPI");

        assertThat(result).isNotNull();
        assertThat(result.message()).contains("KOSHA_OPENAPI");
    }
}
