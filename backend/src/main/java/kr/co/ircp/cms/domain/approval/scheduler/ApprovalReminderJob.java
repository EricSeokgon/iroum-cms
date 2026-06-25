package kr.co.ircp.cms.domain.approval.scheduler;

import kr.co.ircp.cms.domain.approval.repository.UserApprovalMapper;
import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.service.EmailService;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import kr.co.ircp.cms.domain.system.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 가입 승인 대기 리마인더·자동 거절 스케줄러.
 *
 * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-003/004 — {@code QnaNotificationRetryJob}/{@code PostPublishJob}
 * 스타일의 {@code @Component} + {@code @Scheduled} 잡. Spring Batch 미도입(단일 노드 가정).
 * 임계값은 {@code system_setting} key-value 에서 읽으며, 미설정/파싱 실패 시 안전한 기본값으로 회귀한다.
 *
 * <ul>
 *   <li>리마인더: PENDING_APPROVAL + N일 경과 + 미발송 → 이메일 1회 + reminder_sent_at 기록(멱등)</li>
 *   <li>자동 거절: PENDING_APPROVAL + maxWaitDays 초과 → INACTIVE 전환(시스템) + 이메일 + audit_log</li>
 * </ul>
 *
 * <p>NFR-UA2-C2 — maxWaitDays &lt;= 0 이면 자동 거절 비활성(기존 무동작 유지).
 */
// @MX:NOTE: [AUTO] 매일 02:00 — PENDING_APPROVAL 대기자 리마인더 발송 + maxWaitDays 초과 시 자동 거절
// @MX:SPEC: SPEC-CMS-USER-APPROVAL-002#REQ-UA2-003
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalReminderJob {

    private static final String REMINDER_DAYS_KEY = "REGISTRATION_APPROVAL_REMINDER_DAYS";
    private static final String MAX_WAIT_DAYS_KEY = "REGISTRATION_APPROVAL_MAX_WAIT_DAYS";
    private static final int DEFAULT_REMINDER_DAYS = 3;
    private static final int DEFAULT_MAX_WAIT_DAYS = 0; // 0 = 비활성(회귀 방지)
    private static final String AUTO_REJECT_REASON = "자동 거절: 승인 대기 기간 초과";

    private final UserApprovalMapper approvalMapper;
    private final SystemSettingService systemSettingService;
    private final EmailEncryptionService emailEncryptionService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    /**
     * 승인 대기 리마인더 발송 (매일 02:00).
     *
     * <p>REQ-UA2-003 — N일 경과 + 미발송 대상에게 1회 발송 후 reminder_sent_at 을 기록한다.
     * markReminderSent 가 reminder_sent_at IS NULL 행만 갱신하므로 동시/재실행에도 멱등하다.
     */
    @Scheduled(cron = "${cms.approval.reminder-cron:0 0 2 * * ?}")
    @Transactional
    public void sendApprovalReminders() {
        int reminderDays = readIntSetting(REMINDER_DAYS_KEY, DEFAULT_REMINDER_DAYS);
        Instant threshold = Instant.now().minus(reminderDays, ChronoUnit.DAYS);

        List<User> targets = approvalMapper.selectReminderTargets(threshold);
        if (targets.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int sent = 0;
        for (User user : targets) {
            // 멱등: 미발송 행만 표시되며, 0 이면 이미 발송됨 → 발송 생략.
            int marked = approvalMapper.markReminderSent(user.getId(), now);
            if (marked == 0) {
                continue;
            }
            long pendingDays = Duration.between(user.getCreatedAt(), now).toDays();
            emailService.sendApprovalReminder(decryptEmail(user), user.getName(), pendingDays);
            sent++;
        }
        log.info("가입 승인 대기 리마인더 발송: {}건", sent);
    }

    /**
     * 승인 대기 자동 거절 (매일 02:00).
     *
     * <p>REQ-UA2-004 — maxWaitDays 초과 대기자를 INACTIVE 로 전환(시스템 처리)하고 이메일·감사로그를 남긴다.
     * maxWaitDays &lt;= 0 이면 기능 비활성(NFR-UA2-C2).
     */
    @Scheduled(cron = "${cms.approval.auto-reject-cron:0 0 2 * * ?}")
    @Transactional
    public void autoRejectExpired() {
        int maxWaitDays = readIntSetting(MAX_WAIT_DAYS_KEY, DEFAULT_MAX_WAIT_DAYS);
        if (maxWaitDays <= 0) {
            // 비활성 — 기존 무동작 유지.
            return;
        }
        Instant threshold = Instant.now().minus(maxWaitDays, ChronoUnit.DAYS);

        List<User> targets = approvalMapper.selectAutoRejectTargets(threshold);
        if (targets.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int rejected = 0;
        for (User user : targets) {
            int updated = approvalMapper.autoReject(user.getId(), AUTO_REJECT_REASON, now);
            if (updated == 0) {
                continue;
            }
            // audit_log: action=UPDATE, entity_type=User, actor_id=NULL(시스템 처리).
            recordAutoRejectAudit(user.getId(), now);
            emailService.sendApprovalAutoRejected(decryptEmail(user), user.getName(), AUTO_REJECT_REASON);
            rejected++;
        }
        log.info("가입 승인 대기 자동 거절: {}건", rejected);
    }

    /** system_setting INT 값 조회 — 미설정/파싱 실패 시 기본값으로 회귀(회귀 방지). */
    private int readIntSetting(String key, int defaultValue) {
        try {
            return Integer.parseInt(systemSettingService.get(key).value().trim());
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    /** 자동 거절 감사 로그 적재(시스템 처리이므로 actorId=NULL). */
    private void recordAutoRejectAudit(long userId, Instant eventTime) {
        auditLogService.record(new AuditLogService.AuditLogRecord(
                eventTime, null, "SYSTEM", "UPDATE", "User", String.valueOf(userId),
                null, null, null, null, null, "INFO", "SUCCESS", null, null));
    }

    /** V26: email 평문 컬럼 제거 — 암호화 컬럼을 복호화하여 평문 email 을 얻는다. */
    private String decryptEmail(User user) {
        if (user.getEmailEncrypted() == null) {
            return user.getEmail();
        }
        return emailEncryptionService.decrypt(new EncryptedEmail(
                user.getEmailEncrypted(),
                user.getEmailIv(),
                user.getEmailTag(),
                user.getEmailKeyVersion() != null ? user.getEmailKeyVersion() : 1));
    }
}
