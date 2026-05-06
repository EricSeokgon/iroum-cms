package kr.co.ircp.cms.domain.policy.program.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramCreateRequest;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramDetail;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSummary;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSyncResult;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramUpdateRequest;
import kr.co.ircp.cms.domain.policy.program.entity.PolicyProgram;
import kr.co.ircp.cms.domain.policy.program.exception.PolicyProgramNotFoundException;
import kr.co.ircp.cms.domain.policy.program.repository.PolicyProgramMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PolicyProgramService 단위 테스트.
 * REQ-POLICY-001
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyProgramService — 정책사업 마스터 CRUD (REQ-POLICY-001)")
class PolicyProgramServiceTest {

    @Mock private PolicyProgramMapper programMapper;

    private PolicyProgramServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PolicyProgramServiceImpl(programMapper);
    }

    private PolicyProgram entity(Long id) {
        return PolicyProgram.builder()
                .id(id).code("KSP-" + id).ministry("MSS")
                .programName("창업도약패키지")
                .targetIndustries(List.of("F4521"))
                .targetRegions(List.of("11000"))
                .applicationStart(Instant.now())
                .applicationEnd(Instant.now().plusSeconds(7 * 86400))
                .status("ACTIVE")
                .build();
    }

    private PolicyProgramCreateRequest sampleCreateRequest() {
        return new PolicyProgramCreateRequest(
                "KSP-001", "MSS", "창업도약패키지",
                "{\"ko\":\"창업도약\",\"en\":\"Startup Leap\"}",
                "<p>설명</p>",
                List.of("F4521"), List.of("11000"),
                10, 100, 1_000_000_000L, 10_000_000_000L,
                12, 120,
                Instant.now(), Instant.now().plusSeconds(7 * 86400),
                100_000_000_000L, 100_000_000L,
                "https://k-startup.go.kr/policy/1", "ACTIVE"
        );
    }

    // ─── CRUD 테스트 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProgram — id 미존재 시 PolicyProgramNotFoundException")
    void getProgram_missing_throws() {
        when(programMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProgram(99L))
                .isInstanceOf(PolicyProgramNotFoundException.class);
    }

    @Test
    @DisplayName("getProgram — 정상 조회 시 PolicyProgramDetail 반환")
    void getProgram_existing_returnsDetail() {
        when(programMapper.findById(1L)).thenReturn(Optional.of(entity(1L)));

        PolicyProgramDetail detail = service.getProgram(1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.code()).isEqualTo("KSP-1");
        assertThat(detail.ministry()).isEqualTo("MSS");
        assertThat(detail.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("listPrograms — 페이징 응답 구성")
    void listPrograms_returnsPageResponse() {
        when(programMapper.findFiltered(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(entity(1L), entity(2L), entity(3L)));
        when(programMapper.countFiltered(any(), any(), any(), any())).thenReturn(3L);

        PageResponse<PolicyProgramSummary> response = service.listPrograms(
                "ACTIVE", null, null, null, 0, 20);

        assertThat(response.content()).hasSize(3);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("createProgram — INSERT 호출 + DTO 반환")
    void createProgram_insertsAndReturnsDetail() {
        PolicyProgramDetail created = service.createProgram(sampleCreateRequest());

        assertThat(created.code()).isEqualTo("KSP-001");
        assertThat(created.programName()).isEqualTo("창업도약패키지");
        verify(programMapper, times(1)).insert(any(PolicyProgram.class));
    }

    @Test
    @DisplayName("createProgram — status 미지정 시 DRAFT 기본값")
    void createProgram_nullStatus_defaultsToDraft() {
        PolicyProgramCreateRequest req = new PolicyProgramCreateRequest(
                "KSP-002", "MSS", "테스트", null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        PolicyProgramDetail created = service.createProgram(req);

        assertThat(created.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("updateProgram — 미존재 시 PolicyProgramNotFoundException")
    void updateProgram_missing_throws() {
        when(programMapper.findById(99L)).thenReturn(Optional.empty());
        PolicyProgramUpdateRequest req = new PolicyProgramUpdateRequest(
                "수정", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.updateProgram(99L, req))
                .isInstanceOf(PolicyProgramNotFoundException.class);
    }

    @Test
    @DisplayName("updateProgram — 부분 업데이트 (programName 만 변경)")
    void updateProgram_partialUpdate() {
        when(programMapper.findById(1L)).thenReturn(Optional.of(entity(1L)));
        PolicyProgramUpdateRequest req = new PolicyProgramUpdateRequest(
                "변경된 이름", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );

        PolicyProgramDetail updated = service.updateProgram(1L, req);

        assertThat(updated.programName()).isEqualTo("변경된 이름");
        verify(programMapper, times(1)).update(any(PolicyProgram.class));
    }

    @Test
    @DisplayName("deleteProgram — 정상 삭제")
    void deleteProgram_existing_deletes() {
        when(programMapper.findById(1L)).thenReturn(Optional.of(entity(1L)));

        service.deleteProgram(1L);

        verify(programMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deleteProgram — 미존재 시 PolicyProgramNotFoundException")
    void deleteProgram_missing_throws() {
        when(programMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProgram(99L))
                .isInstanceOf(PolicyProgramNotFoundException.class);
    }

    // ─── REQ-POLICY-001-D-1: 외부 OpenAPI mock 동기화 ──────────────────────

    @Test
    @DisplayName("syncFromExternal — sourceCode 기본값 K_STARTUP, 결과 객체 정상 반환")
    void syncFromExternal_defaultSource_returnsResult() {
        PolicyProgramSyncResult result = service.syncFromExternal(null);

        assertThat(result.sourceCode()).isEqualTo("K_STARTUP");
        assertThat(result.syncedAt()).isNotNull();
    }

    @Test
    @DisplayName("syncFromExternal — sourceCode 명시 시 그대로 반환")
    void syncFromExternal_explicitSource() {
        PolicyProgramSyncResult result = service.syncFromExternal("MOTIE_RND");

        assertThat(result.sourceCode()).isEqualTo("MOTIE_RND");
    }
}
