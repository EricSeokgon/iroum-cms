package kr.co.ircp.cms.domain.policy.dispatch.service;

import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleCreateRequest;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleResponse;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleConflictException;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleNotFoundException;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchScheduleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PolicyDispatchService 발송 예약 단위 테스트.
 * REQ-POLICY-003 — 야간 차단 + 멱등성 + 상태 전이.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyDispatchService — 야간 차단 + 멱등성 (REQ-POLICY-003)")
class PolicyDispatchServiceTest {

    @Mock private NotificationDispatchScheduleMapper scheduleMapper;

    private PolicyDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PolicyDispatchServiceImpl(scheduleMapper);
    }

    private DispatchScheduleCreateRequest sampleRequest(Instant scheduledAt) {
        return new DispatchScheduleCreateRequest(
                100L, "CLOSING_SOON", "{\"min_score\":70}",
                scheduledAt, List.of("KAKAO", "EMAIL"),
                7L, 10, 1L
        );
    }

    private NotificationDispatchSchedule entity(Long id, String status) {
        return NotificationDispatchSchedule.builder()
                .id(id).scheduleUuid(UUID.randomUUID()).policyId(100L)
                .dispatchType("CLOSING_SOON").targetFilter("{}")
                .scheduledAt(Instant.now().plusSeconds(3600))
                .channels(List.of("KAKAO"))
                .templateId(7L).priority(50).status(status).createdBy(1L)
                .build();
    }

    // ─── REQ-POLICY-003-D-3: 야간 차단 (KST 21:00 ~ 08:00) ────────────────

    @Test
    @DisplayName("야간 차단 — KST 22:30 → 다음날 09:00 KST 로 보정")
    void nightBlock_22_30_adjustsToNextDay9AM() {
        ZonedDateTime kst22_30 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(22, 30), ZoneId.of("Asia/Seoul"));
        Instant adjusted = service.adjustForNighttimeBlock(kst22_30.toInstant());

        ZonedDateTime adjustedKst = adjusted.atZone(ZoneId.of("Asia/Seoul"));
        assertThat(adjustedKst.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(adjustedKst.toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 7));
    }

    @Test
    @DisplayName("야간 차단 — KST 03:00 → 당일 09:00 KST 로 보정")
    void nightBlock_03_00_adjustsToToday9AM() {
        ZonedDateTime kst03 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(3, 0), ZoneId.of("Asia/Seoul"));
        Instant adjusted = service.adjustForNighttimeBlock(kst03.toInstant());

        ZonedDateTime adjustedKst = adjusted.atZone(ZoneId.of("Asia/Seoul"));
        assertThat(adjustedKst.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(adjustedKst.toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 6));
    }

    @Test
    @DisplayName("야간 차단 — KST 21:00 정확히 → 다음날 09:00 보정")
    void nightBlock_21_00_blocksAndAdjusts() {
        ZonedDateTime kst21 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(21, 0), ZoneId.of("Asia/Seoul"));
        Instant adjusted = service.adjustForNighttimeBlock(kst21.toInstant());

        ZonedDateTime adjustedKst = adjusted.atZone(ZoneId.of("Asia/Seoul"));
        assertThat(adjustedKst.toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(adjustedKst.toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("야간 비차단 — KST 14:00 → 보정 없음")
    void daytime_14_00_noAdjustment() {
        ZonedDateTime kst14 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(14, 0), ZoneId.of("Asia/Seoul"));
        Instant original = kst14.toInstant();
        Instant adjusted = service.adjustForNighttimeBlock(original);

        assertThat(adjusted).isEqualTo(original);
    }

    @Test
    @DisplayName("야간 비차단 — KST 08:00 (NIGHT_END 정확) → 보정 없음")
    void daytime_08_00_boundary_noAdjustment() {
        ZonedDateTime kst08 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(8, 0), ZoneId.of("Asia/Seoul"));
        Instant original = kst08.toInstant();
        Instant adjusted = service.adjustForNighttimeBlock(original);

        assertThat(adjusted).isEqualTo(original);
    }

    @Test
    @DisplayName("isNighttime — 21:00 ~ 08:00 미만은 true")
    void isNighttime_boundaries() {
        assertThat(service.isNighttime(LocalTime.of(21, 0))).isTrue();
        assertThat(service.isNighttime(LocalTime.of(23, 59))).isTrue();
        assertThat(service.isNighttime(LocalTime.of(0, 0))).isTrue();
        assertThat(service.isNighttime(LocalTime.of(7, 59))).isTrue();
        assertThat(service.isNighttime(LocalTime.of(8, 0))).isFalse();
        assertThat(service.isNighttime(LocalTime.of(20, 59))).isFalse();
    }

    // ─── 발송 예약 생성 + 야간 보정 통합 ────────────────────────────────────

    @Test
    @DisplayName("createSchedule — 야간 시각 요청 시 nighttimeAdjusted=true + originalScheduledAt 응답")
    void createSchedule_nighttimeRequest_returnsAdjustedFlag() {
        ZonedDateTime kst23 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(23, 0), ZoneId.of("Asia/Seoul"));
        Instant nightInstant = kst23.toInstant();

        DispatchScheduleResponse response = service.createSchedule(sampleRequest(nightInstant));

        assertThat(response.nighttimeAdjusted()).isTrue();
        assertThat(response.originalScheduledAt()).isEqualTo(nightInstant);
        assertThat(response.scheduledAt()).isNotEqualTo(nightInstant);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(scheduleMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("createSchedule — 주간 시각 요청 시 nighttimeAdjusted=false")
    void createSchedule_daytimeRequest_noAdjustment() {
        ZonedDateTime kst14 = ZonedDateTime.of(LocalDate.of(2026, 5, 6),
                LocalTime.of(14, 0), ZoneId.of("Asia/Seoul"));
        Instant dayInstant = kst14.toInstant();

        DispatchScheduleResponse response = service.createSchedule(sampleRequest(dayInstant));

        assertThat(response.nighttimeAdjusted()).isFalse();
        assertThat(response.originalScheduledAt()).isNull();
        assertThat(response.scheduledAt()).isEqualTo(dayInstant);
    }

    // ─── REQ-POLICY-003-D-2: 멱등성 키 ────────────────────────────────────

    @Test
    @DisplayName("idempotency_key — 동일 입력에 대해 동일 SHA-256 해시")
    void idempotencyKey_sameInput_sameHash() {
        String k1 = PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 10L, "CLOSING_SOON");
        String k2 = PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 10L, "CLOSING_SOON");
        assertThat(k1).isEqualTo(k2);
        assertThat(k1).hasSize(64);  // SHA-256 = 64 hex chars
    }

    @Test
    @DisplayName("idempotency_key — 다른 user_id 면 다른 키")
    void idempotencyKey_differentUser_differentHash() {
        String k1 = PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 10L, "CLOSING_SOON");
        String k2 = PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 20L, "CLOSING_SOON");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("idempotency_key — 다른 dispatch_type 면 다른 키")
    void idempotencyKey_differentType_differentHash() {
        String k1 = PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 10L, "CLOSING_SOON");
        String k2 = PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 10L, "REMINDER");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("idempotency_key — null 입력 시 IllegalArgumentException")
    void idempotencyKey_nullInput_throws() {
        assertThatThrownBy(() ->
                PolicyDispatchServiceImpl.generateIdempotencyKey(null, 10L, "CLOSING_SOON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PolicyDispatchServiceImpl.generateIdempotencyKey(1L, null, "CLOSING_SOON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PolicyDispatchServiceImpl.generateIdempotencyKey(1L, 10L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── 상태 전이 (PENDING → PROCESSING / CANCELLED) ──────────────────────

    @Test
    @DisplayName("triggerNow — PENDING 상태에서 PROCESSING 으로 전환 OK")
    void triggerNow_pendingState_succeeds() {
        when(scheduleMapper.findById(1L)).thenReturn(Optional.of(entity(1L, "PENDING")));

        DispatchScheduleResponse response = service.triggerNow(1L);

        assertThat(response.status()).isEqualTo("PROCESSING");
        verify(scheduleMapper, times(1)).updateStatus(1L, "PROCESSING");
    }

    @Test
    @DisplayName("triggerNow — PROCESSING 이후 상태에서 409 Conflict")
    void triggerNow_processingState_throwsConflict() {
        when(scheduleMapper.findById(1L)).thenReturn(Optional.of(entity(1L, "PROCESSING")));

        assertThatThrownBy(() -> service.triggerNow(1L))
                .isInstanceOf(DispatchScheduleConflictException.class);
    }

    @Test
    @DisplayName("triggerNow — 미존재 시 NotFoundException")
    void triggerNow_missing_throwsNotFound() {
        when(scheduleMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerNow(99L))
                .isInstanceOf(DispatchScheduleNotFoundException.class);
    }

    @Test
    @DisplayName("cancelSchedule — PENDING 상태만 취소 가능")
    void cancelSchedule_pending_succeeds() {
        when(scheduleMapper.findById(1L)).thenReturn(Optional.of(entity(1L, "PENDING")));

        service.cancelSchedule(1L);

        verify(scheduleMapper, times(1)).updateStatus(1L, "CANCELLED");
    }

    @Test
    @DisplayName("cancelSchedule — PROCESSING 이후는 409 Conflict")
    void cancelSchedule_processingState_throwsConflict() {
        when(scheduleMapper.findById(1L)).thenReturn(Optional.of(entity(1L, "PROCESSING")));

        assertThatThrownBy(() -> service.cancelSchedule(1L))
                .isInstanceOf(DispatchScheduleConflictException.class);
        verify(scheduleMapper, never()).updateStatus(anyLong(), anyString());
    }
}
