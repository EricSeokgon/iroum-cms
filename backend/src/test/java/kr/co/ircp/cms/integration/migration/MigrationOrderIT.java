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

    // @MX:NOTE: [AUTO] V1~V65 마이그레이션 총 64개 (V11 없음) — 신규 마이그레이션 추가 시 기대값 갱신 필요
    // V50 (admin_role_menu_permissions), V51 (clean_admin_menus_from_public_menu_table),
    // V52 (super_admin_permissions_sync), V53 (kpi_definition_activity_seed, SPEC-CMS-KPI-002),
    // V54 (bbs_post_optimistic_lock, SPEC-CMS-CONTENT-REVISION-001 M1),
    // V55 (shared_content_block, SPEC-CMS-CONTENT-BLOCK-001),
    // V56 (survey_notification_and_rbac, SPEC-CMS-SURVEY-001),
    // V57 (review_system_rbac, SPEC-CMS-REVIEW-001),
    // V58 (ai_tag_recommendation, SPEC-CMS-AI-004),
    // V59 (email_template, SPEC-CMS-EMAIL-TEMPLATE-001), V60 (email_template_send_log),
    // V61 (email_template_seed — 권한 + 기본 템플릿 시드, SPEC-CMS-EMAIL-TEMPLATE-001 T10),
    // V62 (user_registration_approval — 가입 승인 게이트, SPEC-CMS-USER-APPROVAL-001 T0),
    // V63 (points_system — 참여 포인트 지급 시스템, SPEC-CMS-POINTS-001),
    // V64 (notification_template_extension — 알림 템플릿 정식 컬럼 확장 + 발송 RBAC 시드, SPEC-CMS-NOTI-EXT-001),
    // V65 (user_approval_002 — 이메일 인증/리마인더/자동거절, SPEC-CMS-USER-APPROVAL-002 T0) 추가로 64개.
    private static final int EXPECTED_MIGRATION_COUNT = 64;

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
                "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47",
                "48", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58",
                "59", "60", "61", "62", "63", "64", "65");
    }
}
