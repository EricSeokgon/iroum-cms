package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.ExportRequest;
import kr.co.ircp.cms.domain.dashboard.dto.ExportResponse;
import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;
import kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportNotFoundException;
import kr.co.ircp.cms.domain.dashboard.repository.ExportHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExportService 단위 테스트.
 * REQ-VIZ-006 (sync/async, signed URL, 권한, 만료)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService — sync/async + signed URL + 권한/만료 (REQ-VIZ-006)")
class ExportServiceTest {

    @Mock private ExportHistoryMapper historyMapper;

    private ExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExportServiceImpl(historyMapper, "test-secret-please-change",
                "/tmp/iroum-cms-test-export");
    }

    private ExportHistory completedExport(Long id, Long requestorId) {
        return ExportHistory.builder()
                .id(id).requestorId(requestorId)
                .exportType("EXCEL")
                .scope("{\"dashboard_id\":1}")
                .filePath("/tmp/iroum-cms-test-export/" + id + ".xlsx")
                .sizeBytes(1024L).rowCount(100)
                .status("COMPLETED").progressPct(100)
                .requestedAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    // ──────────────────────────────────────────────
    // sync/async decision (REQ-VIZ-006-D-4)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("createExport — 추정 행수 ≤ 10,000 → sync 경로 (status=COMPLETED 즉시)")
    void createExport_smallRows_syncCompleted() {
        Answer<Void> assignId = (InvocationOnMock inv) -> {
            ExportHistory e = inv.getArgument(0);
            e.setId(42L);
            return null;
        };
        org.mockito.Mockito.doAnswer(assignId).when(historyMapper).insert(any());

        ExportRequest req = new ExportRequest(
                "CSV", "{\"dashboard_id\":1,\"row_count_estimate\":500}", false);

        ExportResponse resp = service.createExport(1L, req);

        assertThat(resp.id()).isEqualTo(42L);
        // sync : insert 후 update 로 COMPLETED 마킹
        verify(historyMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("createExport — 추정 행수 > 10,000 → async 경로 (status=PROCESSING)")
    void createExport_largeRows_asyncProcessing() {
        Answer<Void> assignId = (InvocationOnMock inv) -> {
            ExportHistory e = inv.getArgument(0);
            e.setId(50L);
            return null;
        };
        org.mockito.Mockito.doAnswer(assignId).when(historyMapper).insert(any());

        ExportRequest req = new ExportRequest(
                "EXCEL", "{\"dashboard_id\":1,\"row_count_estimate\":50000}", false);

        ExportResponse resp = service.createExport(1L, req);

        assertThat(resp.id()).isEqualTo(50L);
        assertThat(resp.status()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("createExport — async=true 명시 시 무조건 async")
    void createExport_explicitAsync_processing() {
        Answer<Void> assignId = (InvocationOnMock inv) -> {
            ExportHistory e = inv.getArgument(0);
            e.setId(51L);
            return null;
        };
        org.mockito.Mockito.doAnswer(assignId).when(historyMapper).insert(any());

        ExportRequest req = new ExportRequest(
                "CSV", "{\"row_count_estimate\":100}", true);

        ExportResponse resp = service.createExport(1L, req);

        assertThat(resp.status()).isEqualTo("PROCESSING");
    }

    // ──────────────────────────────────────────────
    // signed URL (REQ-VIZ-006-D-5)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getStatus — completed 인 경우 signed URL 포함")
    void getStatus_completed_includesSignedUrl() {
        when(historyMapper.findById(42L)).thenReturn(Optional.of(completedExport(42L, 1L)));

        ExportResponse resp = service.getStatus(42L, 1L);

        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(resp.signedDownloadUrl()).isNotBlank();
        assertThat(resp.signedDownloadUrl()).contains("/api/v1/dashboard/export/42/download");
        assertThat(resp.signedDownloadUrl()).contains("sig=");
    }

    @Test
    @DisplayName("getStatus — processing 인 경우 signed URL 미포함")
    void getStatus_processing_noSignedUrl() {
        ExportHistory processing = completedExport(42L, 1L);
        processing.setStatus("PROCESSING");
        processing.setProgressPct(40);
        when(historyMapper.findById(42L)).thenReturn(Optional.of(processing));

        ExportResponse resp = service.getStatus(42L, 1L);

        assertThat(resp.status()).isEqualTo("PROCESSING");
        assertThat(resp.signedDownloadUrl()).isNull();
    }

    @Test
    @DisplayName("getStatus — 미존재 시 ExportNotFoundException")
    void getStatus_notFound() {
        when(historyMapper.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStatus(999L, 1L))
                .isInstanceOf(ExportNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // 권한 (REQ-VIZ-006-D-5)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("verifyDownload — 본인 export 만 접근 가능, 타 사용자는 ExportAccessDeniedException")
    void verifyDownload_otherUser_denied() {
        when(historyMapper.findById(42L)).thenReturn(Optional.of(completedExport(42L, 99L)));

        assertThatThrownBy(() -> service.verifyDownload(42L, 1L, false, "any-sig"))
                .isInstanceOf(ExportAccessDeniedException.class);
    }

    @Test
    @DisplayName("verifyDownload — SUPER_ADMIN 은 타인 export 다운로드 가능")
    void verifyDownload_superAdmin_allowed() {
        ExportHistory e = completedExport(42L, 99L);
        when(historyMapper.findById(42L)).thenReturn(Optional.of(e));
        String sig = service.signFor(42L, e.getExpiresAt());

        ExportHistory got = service.verifyDownload(42L, 1L, true, sig);
        assertThat(got.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("verifyDownload — 만료된 export 는 ExportExpiredException (410 Gone)")
    void verifyDownload_expired_throws() {
        ExportHistory expired = completedExport(42L, 1L);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(historyMapper.findById(42L)).thenReturn(Optional.of(expired));
        String sig = service.signFor(42L, expired.getExpiresAt());

        assertThatThrownBy(() -> service.verifyDownload(42L, 1L, false, sig))
                .isInstanceOf(ExportExpiredException.class);
    }

    @Test
    @DisplayName("verifyDownload — 잘못된 서명은 ExportAccessDeniedException")
    void verifyDownload_invalidSignature_throws() {
        when(historyMapper.findById(42L)).thenReturn(Optional.of(completedExport(42L, 1L)));

        assertThatThrownBy(() -> service.verifyDownload(42L, 1L, false, "invalid"))
                .isInstanceOf(ExportAccessDeniedException.class);
    }

    @Test
    @DisplayName("listHistory — 본인 export 만 반환")
    void listHistory_ownerOnly() {
        when(historyMapper.findByRequestor(1L, null))
                .thenReturn(List.of(completedExport(42L, 1L), completedExport(43L, 1L)));

        List<ExportResponse> list = service.listHistory(1L, null);

        assertThat(list).hasSize(2);
        assertThat(list).extracting("requestorId").containsOnly(1L);
    }
}
