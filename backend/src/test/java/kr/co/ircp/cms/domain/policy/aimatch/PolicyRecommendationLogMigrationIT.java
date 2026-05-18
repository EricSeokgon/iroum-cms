package kr.co.ircp.cms.domain.policy.aimatch;

import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-AI-002 AC-PM-016 — V32 마이그레이션 검증 IT.
 *
 * <p>ai_policy_recommendation_log 테이블 + 인덱스 4종 + CHECK 제약 2종 생성 확인.
 */
// @MX:SPEC: SPEC-CMS-AI-002
@DisplayName("V32 ai_policy_recommendation_log 마이그레이션 IT (SPEC-CMS-AI-002)")
class PolicyRecommendationLogMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("AC-PM-016: 테이블 + 4 인덱스 + 2 CHECK 제약이 생성된다")
    void v32SchemaApplied() {
        // 테이블 존재
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_name = 'ai_policy_recommendation_log'", Integer.class);
        assertThat(tableCount).isEqualTo(1);

        // 인덱스 4종
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                        "WHERE tablename = 'ai_policy_recommendation_log'", String.class);
        assertThat(indexes).contains(
                "idx_aprl_session",
                "idx_aprl_type_time",
                "idx_aprl_policy_time",
                "idx_aprl_metrics_day");

        // CHECK 제약 2종 (interaction enum + feedback 무결성)
        List<String> checks = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint " +
                        "WHERE conrelid = 'ai_policy_recommendation_log'::regclass " +
                        "AND contype = 'c'", String.class);
        assertThat(checks).contains("chk_aprl_interaction", "chk_aprl_feedback");
    }

    @Test
    @DisplayName("AC-PM-016: chk_aprl_feedback 제약 — VIEWED는 policy_id NULL 강제")
    void feedbackConstraintEnforced() {
        // VIEWED + policy_id NOT NULL → 제약 위반
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO ai_policy_recommendation_log " +
                                "(session_ref, company_profile, interaction_type, policy_id) " +
                                "VALUES ('hash', '{}'::jsonb, 'VIEWED', 1)"))
                .hasMessageContaining("chk_aprl_feedback");

        // CLICKED + policy_id NULL → 제약 위반
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO ai_policy_recommendation_log " +
                                "(session_ref, company_profile, interaction_type, policy_id) " +
                                "VALUES ('hash', '{}'::jsonb, 'CLICKED', NULL)"))
                .hasMessageContaining("chk_aprl_feedback");

        // VIEWED + policy_id NULL → 정상
        int inserted = jdbcTemplate.update(
                "INSERT INTO ai_policy_recommendation_log " +
                        "(session_ref, company_profile, interaction_type, policy_id) " +
                        "VALUES ('hash64', '{}'::jsonb, 'VIEWED', NULL)");
        assertThat(inserted).isEqualTo(1);
        jdbcTemplate.update("DELETE FROM ai_policy_recommendation_log WHERE session_ref = 'hash64'");
    }
}
