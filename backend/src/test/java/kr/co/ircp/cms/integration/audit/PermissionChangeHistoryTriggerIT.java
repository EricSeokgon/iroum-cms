package kr.co.ircp.cms.integration.audit;

import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * permission_change_history APPEND-ONLY 트리거 통합 테스트.
 *
 * <p>SPEC-CMS-002 v0.3.2 §13.A REQ-AUTH-016 — 권한 변경 이력 무결성 검증.
 */
class PermissionChangeHistoryTriggerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long insertRow() {
        jdbcTemplate.update(
                "INSERT INTO permission_change_history(change_type, target_resource, severity) " +
                "VALUES ('ROLE_ASSIGN', 'VIEWER', 'INFO')");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM permission_change_history ORDER BY id DESC LIMIT 1", Long.class);
    }

    @Test
    @Transactional
    void permissionChangeHistory_INSERT_succeeds() {
        // when
        jdbcTemplate.update(
                "INSERT INTO permission_change_history(change_type, target_resource, severity) " +
                "VALUES ('ROLE_ASSIGN', 'EDITOR', 'INFO')");

        // then
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM permission_change_history WHERE target_resource='EDITOR'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void permissionChangeHistory_UPDATE_throwsException() {
        long id = insertRow();

        assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE permission_change_history SET severity='CRITICAL' WHERE id=?", id)
        ).isInstanceOf(org.springframework.dao.DataAccessException.class)
         .hasMessageContaining("APPEND-ONLY");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void permissionChangeHistory_DELETE_throwsException() {
        long id = insertRow();

        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM permission_change_history WHERE id=?", id)
        ).isInstanceOf(org.springframework.dao.DataAccessException.class)
         .hasMessageContaining("APPEND-ONLY");
    }
}
