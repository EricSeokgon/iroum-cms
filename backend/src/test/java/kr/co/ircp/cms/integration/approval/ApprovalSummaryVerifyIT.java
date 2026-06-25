package kr.co.ircp.cms.integration.approval;

import kr.co.ircp.cms.domain.approval.dto.UserApprovalSummary;
import kr.co.ircp.cms.domain.approval.service.UserApprovalService;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-USER-APPROVAL-002 — 대기열 요약에 이메일 인증 완료 여부 노출 검증 (REQ-UA2-008, AC-UA2-008-3).
 *
 * <p>프론트가 인증 완료/미인증 컬럼을 렌더링할 수 있도록 {@link UserApprovalSummary} 가
 * {@code emailVerifiedAt} 을 전달하는지 확인한다.
 */
@DisplayName("대기열 이메일 인증 표시 IT (SPEC-CMS-USER-APPROVAL-002)")
class ApprovalSummaryVerifyIT extends AbstractIntegrationTest {

    @Autowired private UserApprovalService approvalService;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long insertPending(String username) {
        User user = User.builder()
                .username(username)
                .email(username)
                .passwordHash("$2a$12$placeholder_hash_for_test_only_____")
                .name("인증표시")
                .status(UserStatus.PENDING_APPROVAL)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);
        return user.getId();
    }

    private UserApprovalSummary findSummary(long id) {
        return approvalService.getPendingApprovals(0, 200, null).content().stream()
                .filter(s -> s.userId() == id)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("AC-UA2-008-3 — 미인증 사용자: emailVerifiedAt 이 null")
    void summary_unverified_hasNullVerifiedAt() {
        long id = insertPending("sum_unv_" + System.nanoTime() + "@example.com");

        assertThat(findSummary(id).emailVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("AC-UA2-008-3 — 인증 완료 사용자: emailVerifiedAt 이 not null")
    void summary_verified_hasVerifiedAt() {
        long id = insertPending("sum_ver_" + System.nanoTime() + "@example.com");
        jdbcTemplate.update("UPDATE users SET email_verified_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), id);

        assertThat(findSummary(id).emailVerifiedAt()).isNotNull();
    }
}
