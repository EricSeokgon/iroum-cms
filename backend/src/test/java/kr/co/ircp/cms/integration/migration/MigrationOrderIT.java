package kr.co.ircp.cms.integration.migration;

import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 마이그레이션 순서 검증 통합 테스트.
 *
 * <p>전체 마이그레이션이 올바른 순서로 적용되었는지
 * flyway_schema_history 테이블을 직접 조회하여 검증한다.
 *
 * <p>SPEC-CMS-002 — 마이그레이션 누락·순서 오류 조기 탐지.
 */
class MigrationOrderIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // @MX:NOTE: [AUTO] V1~V44 마이그레이션 총 43개 (V11 없음) — 신규 마이그레이션 추가 시 기대값 갱신 필요
    // V43 (bbs_post_scheduled_publish), V44 (qna_hidden_constraint_fix) 추가로 43개.
    private static final int EXPECTED_MIGRATION_COUNT = 43;

    @Test
    void allMigrationsApplied_inOrder() {
        // when — flyway_schema_history에서 성공한 버전 마이그레이션만 조회
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, description, success " +
                "FROM flyway_schema_history " +
                "WHERE type = 'SQL' " +
                "ORDER BY installed_rank ASC");

        // then — 23개 모두 성공
        assertThat(rows)
                .as("Flyway 마이그레이션이 모두 적용되어야 합니다")
                .hasSize(EXPECTED_MIGRATION_COUNT);

        // then — 모든 마이그레이션 성공 상태
        assertThat(rows)
                .allSatisfy(row ->
                        assertThat((Boolean) row.get("success"))
                                .as("마이그레이션 %s 가 실패 상태입니다", row.get("version"))
                                .isTrue());

        // then — 버전 순서 검증 (V11 없음)
        List<String> versions = rows.stream()
                .map(row -> (String) row.get("version"))
                .toList();
        assertThat(versions).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "12", "13", "14", "15", "16", "17", "18", "19", "20", "21",
                "22", "23", "24", "25", "26", "27", "28", "29", "30", "31",
                "32", "33", "34", "35", "36",
                "37", "38", "39", "40", "41", "42", "43", "44");
    }
}
