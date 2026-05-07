package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;
import kr.co.ircp.cms.domain.governance.repository.BatchExecutionLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BatchExecutionLogService GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010 — start/success/failure/skip 라이프사이클 단위 테스트.
 * 14개 거버넌스 배치 Job이 모두 의존하는 ANCHOR 클래스.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchExecutionLogService GREEN 테스트 (REQ-DATA-005, REQ-GOV-010)")
class BatchExecutionLogServiceTest {

    @Mock
    private BatchExecutionLogMapper mapper;

    private BatchExecutionLogService service;

    @BeforeEach
    void setUp() {
        service = new BatchExecutionLogService(mapper);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private BatchExecutionLog stubRunningLog(Long id, Instant startedAt) {
        return BatchExecutionLog.builder()
                .id(id)
                .jobName("BoardStatsDailyJob")
                .jobGroup("STATS")
                .startedAt(startedAt)
                .status("RUNNING")
                .recordsProcessed(0)
                .recordsFailed(0)
                .retryCount(0)
                .triggeredBy("SCHEDULE")
                .build();
    }

    // ──────────────────────────────────────────────
    // start
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("start — 신규 RUNNING 로그 INSERT + status/startedAt/triggeredBy 기본값 설정")
    void start_insertsRunningLogWithDefaults() {
        // arrange — mapper.insert가 호출될 때 id를 채워주도록 모킹
        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            BatchExecutionLog l = invocation.getArgument(0);
            // 리플렉션 없이 setId 사용 (Lombok @Setter)
            l.setId(123L);
            return null;
        }).when(mapper).insert(any(BatchExecutionLog.class));

        // act
        Long logId = service.start("BoardStatsDailyJob", "STATS");

        // assert
        verify(mapper).insert(captor.capture());
        BatchExecutionLog inserted = captor.getValue();
        assertThat(inserted.getJobName()).isEqualTo("BoardStatsDailyJob");
        assertThat(inserted.getJobGroup()).isEqualTo("STATS");
        assertThat(inserted.getStatus()).isEqualTo("RUNNING");
        assertThat(inserted.getStartedAt()).isNotNull();
        assertThat(inserted.getRecordsProcessed()).isEqualTo(0);
        assertThat(inserted.getRecordsFailed()).isEqualTo(0);
        assertThat(inserted.getRetryCount()).isEqualTo(0);
        assertThat(inserted.getTriggeredBy()).isEqualTo("SCHEDULE");
        assertThat(logId).isEqualTo(123L);
    }

    @Test
    @DisplayName("start — jobGroup 별 다양한 값 (RETENTION/QUALITY/RECOVERY) 모두 인서트")
    void start_acceptsVariousJobGroups() {
        org.mockito.Mockito.doAnswer(invocation -> {
            ((BatchExecutionLog) invocation.getArgument(0)).setId(1L);
            return null;
        }).when(mapper).insert(any(BatchExecutionLog.class));

        service.start("RetentionDeleteJob", "RETENTION");
        service.start("DataQualityCheckJob", "QUALITY");
        service.start("RecoveryDrillReminderJob", "RECOVERY");

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(BatchExecutionLog::getJobGroup)
                .containsExactly("RETENTION", "QUALITY", "RECOVERY");
    }

    // ──────────────────────────────────────────────
    // success
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("success — RUNNING → SUCCESS 전환, finishedAt/durationMs/recordsProcessed 갱신")
    void success_updatesStatusAndDuration() {
        Instant startedAt = Instant.now().minusMillis(500);
        BatchExecutionLog running = stubRunningLog(10L, startedAt);
        when(mapper.findById(10L)).thenReturn(Optional.of(running));

        service.success(10L, 100);

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper).update(captor.capture());
        BatchExecutionLog updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("SUCCESS");
        assertThat(updated.getFinishedAt()).isNotNull();
        assertThat(updated.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(updated.getRecordsProcessed()).isEqualTo(100);
        assertThat(updated.getErrorSummary()).isNull();
    }

    @Test
    @DisplayName("success — 존재하지 않는 logId는 NoSuchElementException")
    void success_nonExistentId_throws() {
        when(mapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.success(999L, 50))
                .isInstanceOf(NoSuchElementException.class);

        verify(mapper, never()).update(any());
    }

    @Test
    @DisplayName("success — startedAt이 null이면 durationMs=0")
    void success_nullStartedAt_durationZero() {
        BatchExecutionLog running = stubRunningLog(11L, null);
        when(mapper.findById(11L)).thenReturn(Optional.of(running));

        service.success(11L, 5);

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper).update(captor.capture());
        assertThat(captor.getValue().getDurationMs()).isEqualTo(0);
        assertThat(captor.getValue().getRecordsProcessed()).isEqualTo(5);
    }

    // ──────────────────────────────────────────────
    // failure
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("failure — RUNNING → FAILURE 전환, errorSummary 기록")
    void failure_updatesStatusAndErrorSummary() {
        BatchExecutionLog running = stubRunningLog(20L, Instant.now().minusMillis(200));
        when(mapper.findById(20L)).thenReturn(Optional.of(running));

        service.failure(20L, "Connection refused: localhost:5432");

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper).update(captor.capture());
        BatchExecutionLog updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("FAILURE");
        assertThat(updated.getFinishedAt()).isNotNull();
        assertThat(updated.getErrorSummary()).isEqualTo("Connection refused: localhost:5432");
    }

    @Test
    @DisplayName("failure — errorSummary가 1000자 초과 시 truncate")
    void failure_longErrorSummary_truncatesTo1000Chars() {
        BatchExecutionLog running = stubRunningLog(21L, Instant.now());
        when(mapper.findById(21L)).thenReturn(Optional.of(running));

        String longError = "X".repeat(1500);

        service.failure(21L, longError);

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper).update(captor.capture());
        assertThat(captor.getValue().getErrorSummary()).hasSize(1000);
    }

    @Test
    @DisplayName("failure — null errorSummary는 그대로 null")
    void failure_nullErrorSummary_remainsNull() {
        BatchExecutionLog running = stubRunningLog(22L, Instant.now());
        when(mapper.findById(22L)).thenReturn(Optional.of(running));

        service.failure(22L, null);

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper).update(captor.capture());
        assertThat(captor.getValue().getErrorSummary()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("failure — 존재하지 않는 logId는 NoSuchElementException")
    void failure_nonExistentId_throws() {
        when(mapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.failure(999L, "error"))
                .isInstanceOf(NoSuchElementException.class);

        verify(mapper, never()).update(any());
    }

    // ──────────────────────────────────────────────
    // skip
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("skip — SKIPPED 상태로 전환 + reason 저장 + durationMs=0")
    void skip_updatesStatusToSkipped() {
        BatchExecutionLog running = stubRunningLog(30L, Instant.now());
        when(mapper.findById(30L)).thenReturn(Optional.of(running));

        service.skip(30L, "SPEC-CMS-008 미반영");

        ArgumentCaptor<BatchExecutionLog> captor = ArgumentCaptor.forClass(BatchExecutionLog.class);
        verify(mapper).update(captor.capture());
        BatchExecutionLog updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("SKIPPED");
        assertThat(updated.getDurationMs()).isEqualTo(0);
        assertThat(updated.getFinishedAt()).isNotNull();
        assertThat(updated.getErrorSummary()).isEqualTo("SPEC-CMS-008 미반영");
    }

    @Test
    @DisplayName("skip — 존재하지 않는 logId는 NoSuchElementException")
    void skip_nonExistentId_throws() {
        when(mapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.skip(999L, "reason"))
                .isInstanceOf(NoSuchElementException.class);

        verify(mapper, never()).update(any());
    }

    // ──────────────────────────────────────────────
    // cleanupOlderThan
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("cleanupOlderThan — threshold 이전 행 삭제 후 영향받은 행 수 반환")
    void cleanupOlderThan_returnsDeletedCount() {
        when(mapper.deleteOlderThan(any(Instant.class))).thenReturn(7);

        int deleted = service.cleanupOlderThan(90);

        assertThat(deleted).isEqualTo(7);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(mapper).deleteOlderThan(captor.capture());
        // threshold는 ~90일 전이어야 함 (테스트 시점 기준)
        Instant threshold = captor.getValue();
        Instant nowMinus89Days = Instant.now().minus(java.time.Duration.ofDays(89));
        Instant nowMinus91Days = Instant.now().minus(java.time.Duration.ofDays(91));
        assertThat(threshold).isBefore(nowMinus89Days).isAfter(nowMinus91Days);
    }

    // ──────────────────────────────────────────────
    // findById
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findById — mapper 위임")
    void findById_delegatesToMapper() {
        BatchExecutionLog log = stubRunningLog(40L, Instant.now());
        when(mapper.findById(40L)).thenReturn(Optional.of(log));

        Optional<BatchExecutionLog> result = service.findById(40L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(40L);
        verify(mapper).findById(40L);
    }

    // ──────────────────────────────────────────────
    // findFiltered (페이징)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findFiltered — 필터 + 페이징 + total 매핑하여 PageResponse 반환")
    void findFiltered_returnsPageResponse() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        BatchExecutionLog row = stubRunningLog(50L, from);
        when(mapper.findFiltered(any())).thenReturn(List.of(row));
        when(mapper.countFiltered(any())).thenReturn(1);

        PageResponse<BatchExecutionLog> result = service.findFiltered(
                "STATS", "SUCCESS", from, to, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findFiltered(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("jobGroup", "STATS");
        assertThat(params).containsEntry("status", "SUCCESS");
        assertThat(params).containsEntry("from", from);
        assertThat(params).containsEntry("to", to);
        assertThat(params).containsEntry("offset", 0);
        assertThat(params).containsEntry("size", 20);
    }

    @Test
    @DisplayName("findFiltered — page=2 size=10 시 offset=20 매핑")
    void findFiltered_page2Size10_offset20() {
        when(mapper.findFiltered(any())).thenReturn(List.of());
        when(mapper.countFiltered(any())).thenReturn(0);

        service.findFiltered(null, null, null, null, 2, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findFiltered(captor.capture());
        assertThat(captor.getValue()).containsEntry("offset", 20);
        assertThat(captor.getValue()).containsEntry("size", 10);
    }
}
