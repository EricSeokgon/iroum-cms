package kr.co.ircp.cms.integration.approval;

import kr.co.ircp.cms.config.IntegrationAsyncConfig;
import kr.co.ircp.cms.domain.approval.scheduler.ApprovalReminderJob;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-USER-APPROVAL-002 — 승인 대기 리마인더/자동 거절 스케줄러 통합 테스트 (실제 PostgreSQL).
 *
 * <p>REQ-UA2-003 리마인더(AC-003-1~4), REQ-UA2-004 자동 거절(AC-004-1~4) 검증.
 * 잡 메서드를 직접 호출하여 cron 트리거 없이 비즈니스 로직만 검증한다.
 */
@DisplayName("승인 대기 리마인더/자동 거절 스케줄러 IT (SPEC-CMS-USER-APPROVAL-002)")
@Import(IntegrationAsyncConfig.class)
class ApprovalSchedulerIT extends AbstractIntegrationTest {

    @Autowired private ApprovalReminderJob job;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String REMINDER_KEY = "REGISTRATION_APPROVAL_REMINDER_DAYS";
    private static final String MAX_WAIT_KEY = "REGISTRATION_APPROVAL_MAX_WAIT_DAYS";

    @AfterEach
    void resetSettings() {
        jdbcTemplate.update("UPDATE system_setting SET value = '3' WHERE key = ?", REMINDER_KEY);
        jdbcTemplate.update("UPDATE system_setting SET value = '0' WHERE key = ?", MAX_WAIT_KEY);
    }

    private void setSetting(String key, int days) {
        jdbcTemplate.update("UPDATE system_setting SET value = ? WHERE key = ?", String.valueOf(days), key);
    }

    /** PENDING_APPROVAL 사용자를 createdAt 을 과거로 backdate 하여 시드한다. */
    private long insertPending(String username, int daysAgo) {
        User user = User.builder()
                .username(username)
                .email(username)
                .passwordHash("$2a$12$placeholder_hash_for_test_only_____")
                .name("스케줄러대상")
                .status(UserStatus.PENDING_APPROVAL)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);
        // created_at 을 daysAgo 일 전으로 강제 backdate (insert 는 NOW() 고정)
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)), user.getId());
        return user.getId();
    }

    private String status(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, id);
    }

    private Instant reminderSentAt(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT reminder_sent_at FROM users WHERE id = ?", Instant.class, id);
    }

    // ─── REQ-UA2-003 리마인더 ──────────────────────────────────────

    @Test
    @DisplayName("AC-UA2-003-1 — 임계 초과 + 미발송: 리마인더 1회 발송 + reminder_sent_at 기록")
    void reminder_overThreshold_sendsAndRecords() {
        setSetting(REMINDER_KEY, 3);
        long id = insertPending("rem_due_" + System.nanoTime() + "@example.com", 4);

        job.sendApprovalReminders();

        assertThat(reminderSentAt(id)).isNotNull();
    }

    @Test
    @DisplayName("AC-UA2-003-2 — 이미 발송된 사용자: 재실행 시 중복 미발송(멱등)")
    void reminder_alreadySent_isIdempotent() {
        setSetting(REMINDER_KEY, 3);
        long id = insertPending("rem_idem_" + System.nanoTime() + "@example.com", 4);

        job.sendApprovalReminders();
        Instant firstSent = reminderSentAt(id);
        assertThat(firstSent).isNotNull();

        job.sendApprovalReminders();
        Instant secondSent = reminderSentAt(id);
        // 재실행해도 reminder_sent_at 이 갱신되지 않아야 한다(중복 미발송).
        assertThat(secondSent).isEqualTo(firstSent);
    }

    @Test
    @DisplayName("AC-UA2-003-3 — 임계 미만 경과: 리마인더 미발송")
    void reminder_underThreshold_skips() {
        setSetting(REMINDER_KEY, 3);
        long id = insertPending("rem_young_" + System.nanoTime() + "@example.com", 2);

        job.sendApprovalReminders();

        assertThat(reminderSentAt(id)).isNull();
    }

    @Test
    @DisplayName("AC-UA2-003-4 — PENDING_APPROVAL 아님: 대상 제외")
    void reminder_notPending_excluded() {
        setSetting(REMINDER_KEY, 3);
        long id = insertPending("rem_active_" + System.nanoTime() + "@example.com", 5);
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", id);

        job.sendApprovalReminders();

        assertThat(reminderSentAt(id)).isNull();
    }

    // ─── REQ-UA2-004 자동 거절 ─────────────────────────────────────

    @Test
    @DisplayName("AC-UA2-004-1/4 — 임계 초과: INACTIVE 전환 + 자동 거절 사유 + approval_changed_by NULL")
    void autoReject_overThreshold_setsInactive() {
        setSetting(MAX_WAIT_KEY, 14);
        long id = insertPending("auto_due_" + System.nanoTime() + "@example.com", 15);

        job.autoRejectExpired();

        assertThat(status(id)).isEqualTo("INACTIVE");
        String reason = jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM users WHERE id = ?", String.class, id);
        assertThat(reason).contains("자동 거절");
        Long changedBy = jdbcTemplate.queryForObject(
                "SELECT approval_changed_by FROM users WHERE id = ?", Long.class, id);
        assertThat(changedBy).isNull();
    }

    @Test
    @DisplayName("AC-UA2-004-1 — 자동 거절 시 audit_log(action=UPDATE, entity_type=User) 기록")
    void autoReject_writesAuditLog() {
        setSetting(MAX_WAIT_KEY, 14);
        long id = insertPending("auto_audit_" + System.nanoTime() + "@example.com", 20);

        job.autoRejectExpired();

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'UPDATE' AND entity_type = 'User' AND entity_id = ?",
                Long.class, String.valueOf(id));
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("AC-UA2-004-2 — MAX_WAIT_DAYS=0(비활성): 어떤 사용자도 자동 거절되지 않음")
    void autoReject_disabled_noChange() {
        setSetting(MAX_WAIT_KEY, 0);
        long id = insertPending("auto_off_" + System.nanoTime() + "@example.com", 30);

        job.autoRejectExpired();

        assertThat(status(id)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("AC-UA2-004-3 — 임계 미만 경과: 자동 거절되지 않고 대기 유지")
    void autoReject_underThreshold_keepsPending() {
        setSetting(MAX_WAIT_KEY, 14);
        long id = insertPending("auto_young_" + System.nanoTime() + "@example.com", 10);

        job.autoRejectExpired();

        assertThat(status(id)).isEqualTo("PENDING_APPROVAL");
    }
}
