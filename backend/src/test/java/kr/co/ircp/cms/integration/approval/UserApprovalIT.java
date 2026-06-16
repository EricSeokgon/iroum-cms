package kr.co.ircp.cms.integration.approval;

import kr.co.ircp.cms.domain.approval.dto.BulkOperationResult;
import kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException;
import kr.co.ircp.cms.domain.approval.service.UserApprovalService;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC-CMS-USER-APPROVAL-001 — 가입 승인 워크플로 통합 테스트 (실제 PostgreSQL).
 *
 * <p>대기열 조회(REQ-UA-007), 승인(REQ-UA-010), 거절(REQ-UA-011), 409(REQ-UA-013),
 * 일괄 처리(REQ-UA-014~016), 메타데이터 영속화(REQ-UA-021) 검증.
 */
@DisplayName("UserApproval 통합 테스트 (SPEC-CMS-USER-APPROVAL-001)")
class UserApprovalIT extends AbstractIntegrationTest {

    @Autowired private UserApprovalService approvalService;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long OPERATOR = 1L; // V2 시드 super_admin

    private long insertPendingUser(String username, String name) {
        User user = User.builder()
                .username(username)
                .email(username)
                .passwordHash("$2a$12$placeholder_hash_for_test_only_____")
                .name(name)
                .status(UserStatus.PENDING_APPROVAL)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);
        return user.getId();
    }

    @Test
    @DisplayName("REQ-UA-007 — 대기열 조회: PENDING_APPROVAL 사용자만 가입일시 오름차순 반환")
    void getPendingApprovals_returnsOnlyPending() {
        long id1 = insertPendingUser("queue_a@example.com", "대기A");
        long id2 = insertPendingUser("queue_b@example.com", "대기B");

        UserApprovalService.PageResult result =
                approvalService.getPendingApprovals(0, 50, null);

        List<Long> ids = result.content().stream().map(s -> s.userId()).toList();
        assertThat(ids).contains(id1, id2);
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("REQ-UA-008 — 대기열 검색: 이름 키워드로 필터링")
    void getPendingApprovals_filtersByKeyword() {
        insertPendingUser("search_x@example.com", "유니크검색대상");

        UserApprovalService.PageResult result =
                approvalService.getPendingApprovals(0, 50, "유니크검색");

        assertThat(result.content()).isNotEmpty();
        assertThat(result.content()).allMatch(s -> s.name().contains("유니크검색"));
    }

    @Test
    @DisplayName("REQ-UA-010/021 — 승인: ACTIVE 전환 + MEMBER 역할 + 메타데이터 영속화")
    void approve_setsActiveAndPersistsMetadata() {
        long userId = insertPendingUser("approve_me@example.com", "승인대상");

        approvalService.approve(userId, OPERATOR);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?", String.class, userId);
        assertThat(status).isEqualTo("ACTIVE");

        Long changedBy = jdbcTemplate.queryForObject(
                "SELECT approval_changed_by FROM users WHERE id = ?", Long.class, userId);
        assertThat(changedBy).isEqualTo(OPERATOR);

        assertThat(userMapper.findRoleCodesByUserId(userId)).contains("MEMBER");
    }

    @Test
    @DisplayName("REQ-UA-011/021 — 거절: INACTIVE 전환 + 거절 사유 저장")
    void reject_setsInactiveAndStoresReason() {
        long userId = insertPendingUser("reject_me@example.com", "거절대상");

        approvalService.reject(userId, "자격 미달", OPERATOR);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?", String.class, userId);
        assertThat(status).isEqualTo("INACTIVE");

        String reason = jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM users WHERE id = ?", String.class, userId);
        assertThat(reason).isEqualTo("자격 미달");
    }

    @Test
    @DisplayName("REQ-UA-013 — 이미 승인된 사용자 재승인 시도: 409(UserNotPendingApprovalException)")
    void approve_alreadyActive_throwsConflict() {
        long userId = insertPendingUser("double_approve@example.com", "중복승인");
        approvalService.approve(userId, OPERATOR);

        assertThatThrownBy(() -> approvalService.approve(userId, OPERATOR))
                .isInstanceOf(UserNotPendingApprovalException.class);
    }

    @Test
    @DisplayName("REQ-UA-014/016 — 일괄 승인: 대기 + 비대기 혼합 시 건별 집계")
    void bulkApprove_mixedStatuses_aggregates() {
        long pending = insertPendingUser("bulk_pending@example.com", "일괄대기");
        long active = insertPendingUser("bulk_active@example.com", "일괄활성");
        approvalService.approve(active, OPERATOR); // active 를 미리 ACTIVE 로 — 재승인 실패 유도

        BulkOperationResult result = approvalService.bulkApprove(List.of(pending, active), OPERATOR);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).userId()).isEqualTo(active);
    }
}
