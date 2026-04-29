package kr.co.ircp.cms.integration.audit;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * personal_data_access_log APPEND-ONLY 트리거 통합 테스트.
 *
 * <p>REQ-AUTH-018-D-1 개인정보보호법 §29 무결성 보장 트리거 검증.
 */
class PersonalDataAccessTriggerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    private long viewerId;
    private long targetId;

    @BeforeEach
    @Transactional
    void setUpUsers() {
        User viewer = User.builder()
                .username("pda_viewer_it")
                .email("pda_viewer@example.com")
                .passwordHash("$2a$12$viewer_hash_____________________")
                .name("조회자")
                .status(UserStatus.ACTIVE)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(viewer);
        viewerId = viewer.getId();

        User target = User.builder()
                .username("pda_target_it")
                .email("pda_target@example.com")
                .passwordHash("$2a$12$target_hash_____________________")
                .name("대상자")
                .status(UserStatus.ACTIVE)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(target);
        targetId = target.getId();
    }

    private long insertPdaRow() {
        jdbcTemplate.update(
                "INSERT INTO personal_data_access_log(viewer_id, target_user_id, accessed_fields, purpose) " +
                "VALUES (?, ?, '[\"email\"]'::jsonb, 'SUPPORT')",
                viewerId, targetId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM personal_data_access_log ORDER BY id DESC LIMIT 1", Long.class);
    }

    @Test
    @Transactional
    void personalDataAccessLog_INSERT_succeeds() {
        // when
        jdbcTemplate.update(
                "INSERT INTO personal_data_access_log(viewer_id, target_user_id, accessed_fields, purpose) " +
                "VALUES (?, ?, '[\"name\"]'::jsonb, 'AUDIT')",
                viewerId, targetId);

        // then
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personal_data_access_log WHERE purpose='AUDIT'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void personalDataAccessLog_UPDATE_throwsException() {
        long id = insertPdaRow();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE personal_data_access_log SET purpose='EXPORT' WHERE id=?", id)
        ).isInstanceOf(org.springframework.dao.DataAccessException.class)
         .hasMessageContaining("APPEND-ONLY");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void personalDataAccessLog_DELETE_throwsException() {
        long id = insertPdaRow();

        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM personal_data_access_log WHERE id=?", id)
        ).isInstanceOf(org.springframework.dao.DataAccessException.class)
         .hasMessageContaining("APPEND-ONLY");
    }
}
