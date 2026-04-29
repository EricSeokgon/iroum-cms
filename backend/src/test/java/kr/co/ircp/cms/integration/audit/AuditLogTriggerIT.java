package kr.co.ircp.cms.integration.audit;

import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * audit_log APPEND-ONLY 트리거 통합 테스트.
 *
 * <p>SPEC-CMS-005 v0.2.1 §7.4 — UPDATE/DELETE 차단 트리거 동작 검증.
 * 트리거가 RAISE EXCEPTION을 던지면 JdbcTemplate이 DataAccessException을 래핑한다.
 */
class AuditLogTriggerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long insertRow() {
        jdbcTemplate.update(
                "INSERT INTO audit_log(action, severity, result) VALUES ('CREATE', 'INFO', 'SUCCESS')");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM audit_log ORDER BY id DESC LIMIT 1", Long.class);
    }

    @Test
    @Transactional
    void auditLog_INSERT_succeeds() {
        // when
        jdbcTemplate.update(
                "INSERT INTO audit_log(action, severity, result) VALUES ('LOGIN', 'INFO', 'SUCCESS')");

        // then
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT action FROM audit_log WHERE action='LOGIN' LIMIT 1");
        assertThat(row.get("action")).isEqualTo("LOGIN");
    }

    /**
     * UPDATE 시도 시 트리거가 예외를 던지는지 검증.
     * 트리거 예외는 트랜잭션을 무효화하므로 REQUIRES_NEW 사용.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditLog_UPDATE_throwsException() {
        long id = insertRow();

        // when / then
        // @MX:NOTE: [AUTO] PSQLException 대신 DataAccessException 래핑으로 검증
        assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE audit_log SET severity='WARN' WHERE id=?", id)
        ).isInstanceOf(org.springframework.dao.DataAccessException.class)
         .hasMessageContaining("APPEND-ONLY");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditLog_DELETE_throwsException() {
        long id = insertRow();

        // when / then
        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM audit_log WHERE id=?", id)
        ).isInstanceOf(org.springframework.dao.DataAccessException.class)
         .hasMessageContaining("APPEND-ONLY");
    }
}
