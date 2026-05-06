package kr.co.ircp.cms.domain.policy.dispatch.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleCreateRequest;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleResponse;
import kr.co.ircp.cms.domain.policy.dispatch.entity.NotificationDispatchSchedule;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleConflictException;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleNotFoundException;
import kr.co.ircp.cms.domain.policy.dispatch.repository.NotificationDispatchScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 정책 알림 발송 예약 구현.
 *
 * 핵심 비기능:
 * - 야간 차단 (REQ-POLICY-003-D-3): KST 21:00 ~ 08:00 → 다음 09:00 KST 로 보정
 * - 멱등성 (REQ-POLICY-003-D-2): SHA-256(schedule_id || user_id || dispatch_type)
 *
 * // @MX:WARN: [AUTO] 야간 차단 보정 로직은 대량 발송에서 폭주를 야기할 수 있음 (예: 21:00 직전 대량 등록)
 * // @MX:REASON: 보정된 09:00 KST 시간대에 정책 마감 시즌이 겹치면 수만 건이 동일 시각 큐에 누적될 수 있음. ShedLock + 우선순위 큐로 완화 필요.
 * // @MX:SPEC: REQ-POLICY-003-D-3
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyDispatchServiceImpl implements PolicyDispatchService {

    /** KST 야간 차단 시작 시각 (포함). */
    public static final LocalTime NIGHT_START = LocalTime.of(21, 0);
    /** KST 야간 차단 종료 시각 (미포함, 즉 < 08:00 차단). */
    public static final LocalTime NIGHT_END = LocalTime.of(8, 0);
    /** 보정 시 다음 발송 가능 시각 (KST 09:00). */
    public static final LocalTime ADJUST_TARGET = LocalTime.of(9, 0);
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationDispatchScheduleMapper scheduleMapper;

    @Override
    public PageResponse<DispatchScheduleResponse> listSchedules(String status, Long policyId, int page, int size) {
        int offset = page * size;
        List<NotificationDispatchSchedule> rows = scheduleMapper.findFiltered(status, policyId, offset, size);
        long total = scheduleMapper.countFiltered(status, policyId);
        List<DispatchScheduleResponse> content = rows.stream()
                .map(s -> toResponse(s, false, null))
                .collect(Collectors.toList());
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public DispatchScheduleResponse createSchedule(DispatchScheduleCreateRequest request) {
        // 야간 차단 자동 보정
        Instant requested = request.scheduledAt();
        Instant adjusted = adjustForNighttimeBlock(requested);
        boolean wasAdjusted = !adjusted.equals(requested);

        NotificationDispatchSchedule entity = NotificationDispatchSchedule.builder()
                .scheduleUuid(UUID.randomUUID())
                .policyId(request.policyId())
                .dispatchType(request.dispatchType())
                .targetFilter(request.targetFilter() == null ? "{}" : request.targetFilter())
                .scheduledAt(adjusted)
                .channels(request.channels())
                .templateId(request.templateId())
                .priority(request.priority() == null ? 50 : request.priority())
                .status("PENDING")
                .createdBy(request.createdBy())
                .build();
        scheduleMapper.insert(entity);

        return toResponse(entity, wasAdjusted, wasAdjusted ? requested : null);
    }

    @Override
    @Transactional
    public DispatchScheduleResponse triggerNow(Long id) {
        NotificationDispatchSchedule existing = scheduleMapper.findById(id)
                .orElseThrow(() -> new DispatchScheduleNotFoundException(id));
        if (!"PENDING".equals(existing.getStatus())) {
            throw new DispatchScheduleConflictException(
                    "트리거 가능한 상태가 아닙니다. 현재 상태=" + existing.getStatus());
        }
        scheduleMapper.updateStatus(id, "PROCESSING");
        existing.setStatus("PROCESSING");
        existing.setStartedAt(Instant.now());
        return toResponse(existing, false, null);
    }

    @Override
    @Transactional
    public void cancelSchedule(Long id) {
        NotificationDispatchSchedule existing = scheduleMapper.findById(id)
                .orElseThrow(() -> new DispatchScheduleNotFoundException(id));
        if (!"PENDING".equals(existing.getStatus())) {
            throw new DispatchScheduleConflictException(
                    "취소 가능한 상태가 아닙니다. 현재 상태=" + existing.getStatus());
        }
        scheduleMapper.updateStatus(id, "CANCELLED");
    }

    // ─── 비기능 핵심 (package-private for testing) ────────────────────────────

    /**
     * 야간 차단 보정.
     * KST 21:00 ~ 08:00 사이 시각이면 다음 09:00 KST 로 미룬다.
     * REQ-POLICY-003-D-3
     */
    Instant adjustForNighttimeBlock(Instant scheduledAt) {
        ZonedDateTime kst = scheduledAt.atZone(KST);
        if (!isNighttime(kst.toLocalTime())) {
            return scheduledAt;
        }
        // 21시 이후이면 다음 날 09:00, 08시 이전이면 당일 09:00
        LocalDateTime targetDateTime;
        if (kst.toLocalTime().isAfter(NIGHT_END) || kst.toLocalTime().equals(NIGHT_END)) {
            // 21:00~ 사이 (NIGHT_END 이후이지만 아직 NIGHT_START 이후) 처리
            // isNighttime 이 true 인 경우만 들어옴 → 21시 이후 또는 08시 이전
            if (kst.toLocalTime().isBefore(NIGHT_START)) {
                // 08시 이전 (당일 새벽) — 당일 09:00
                targetDateTime = kst.toLocalDate().atTime(ADJUST_TARGET);
            } else {
                // 21시 이후 — 다음 날 09:00
                targetDateTime = kst.toLocalDate().plusDays(1).atTime(ADJUST_TARGET);
            }
        } else {
            // < 08:00 → 당일 09:00
            targetDateTime = kst.toLocalDate().atTime(ADJUST_TARGET);
        }
        return targetDateTime.atZone(KST).toInstant();
    }

    /** KST 야간 시간 여부: time >= 21:00 || time < 08:00. */
    boolean isNighttime(LocalTime time) {
        if (time == null) return false;
        if (!time.isBefore(NIGHT_START)) return true;       // 21:00 이상
        return time.isBefore(NIGHT_END);                    // 08:00 미만
    }

    /**
     * 멱등성 키 생성: SHA-256(schedule_id || user_id || dispatch_type).
     * REQ-POLICY-003-D-2
     */
    public static String generateIdempotencyKey(Long scheduleId, Long userId, String dispatchType) {
        if (scheduleId == null || userId == null || dispatchType == null) {
            throw new IllegalArgumentException("idempotency-key 생성에는 scheduleId, userId, dispatchType 모두 필요합니다.");
        }
        String input = scheduleId + "||" + userId + "||" + dispatchType;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다", e);
        }
    }

    DispatchScheduleResponse toResponse(NotificationDispatchSchedule s, boolean nighttimeAdjusted, Instant originalScheduledAt) {
        return new DispatchScheduleResponse(
                s.getId(),
                s.getScheduleUuid(),
                s.getPolicyId(),
                s.getDispatchType(),
                s.getTargetFilter(),
                s.getScheduledAt(),
                nighttimeAdjusted,
                originalScheduledAt,
                s.getChannels(),
                s.getTemplateId(),
                s.getPriority(),
                s.getStatus(),
                s.getCreatedBy(),
                s.getCreatedAt()
        );
    }
}
