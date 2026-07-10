package kr.co.ircp.cms.domain.ai.tag;

import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC-CMS-AI-004 AC-AI-TAG-013/014 — V58 마이그레이션 검증 IT.
 *
 * <p>ai_tag_recommendation_log 테이블 + 인덱스 3종 + CHECK 제약 3종 생성 확인,
 * bbs_post·qna tags 컬럼 additive 추가(기존 행 빈 배열) 무파손 확인.
 */
// @MX:SPEC: SPEC-CMS-AI-004
@DisplayName("V58 ai_tag_recommendation 마이그레이션 IT (SPEC-CMS-AI-004)")
class TagRecommendationLogMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("AC-AI-TAG-013: ai_tag_recommendation_log 테이블 + 인덱스 3종 + CHECK 제약 3종이 생성된다")
    void v54SchemaApplied() {
        // 테이블 존재
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_name = 'ai_tag_recommendation_log'", Integer.class);
        assertThat(tableCount).isEqualTo(1);

        // 주요 컬럼 존재
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'ai_tag_recommendation_log'", String.class);
        assertThat(columns).contains(
                "id", "session_ref", "content_type", "content_hash",
                "recommended_tags", "ml_scores", "model_version",
                "event_type", "tag_value", "suggested_at", "interacted_at");

        // 인덱스 3종
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                        "WHERE tablename = 'ai_tag_recommendation_log'", String.class);
        assertThat(indexes).contains("idx_atrl_session", "idx_atrl_event", "idx_atrl_type_time");

        // CHECK 제약 3종
        List<String> checks = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint " +
                        "WHERE conrelid = 'ai_tag_recommendation_log'::regclass AND contype = 'c'",
                String.class);
        assertThat(checks).contains("chk_atrl_event", "chk_atrl_feedback", "chk_atrl_content_type");
    }

    @Test
    @DisplayName("AC-AI-TAG-013: chk_atrl_feedback — SUGGESTED는 tag_value NULL 강제")
    void feedbackConstraintEnforced() {
        // SUGGESTED + tag_value NOT NULL → 제약 위반
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_tag_recommendation_log " +
                        "(session_ref, content_type, content_hash, event_type, tag_value) " +
                        "VALUES ('hash', 'POST', 'chash', 'SUGGESTED', '태그X')"))
                .hasMessageContaining("chk_atrl_feedback");

        // ACCEPTED + tag_value NULL → 제약 위반
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ai_tag_recommendation_log " +
                        "(session_ref, content_type, content_hash, event_type, tag_value) " +
                        "VALUES ('hash', 'POST', 'chash', 'ACCEPTED', NULL)"))
                .hasMessageContaining("chk_atrl_feedback");

        // SUGGESTED + tag_value NULL → 정상
        int inserted = jdbcTemplate.update(
                "INSERT INTO ai_tag_recommendation_log " +
                        "(session_ref, content_type, content_hash, event_type, tag_value) " +
                        "VALUES ('hash54', 'POST', 'chash54', 'SUGGESTED', NULL)");
        assertThat(inserted).isEqualTo(1);
        jdbcTemplate.update(
                "DELETE FROM ai_tag_recommendation_log WHERE session_ref = 'hash54'");
    }

    @Test
    @DisplayName("AC-AI-TAG-014: bbs_post.tags 컬럼이 빈 배열 기본값으로 존재한다")
    void bbsPostTagsColumnExists() {
        List<String> defaults = jdbcTemplate.queryForList(
                "SELECT column_default FROM information_schema.columns " +
                        "WHERE table_name = 'bbs_post' AND column_name = 'tags'", String.class);
        assertThat(defaults).hasSize(1);
        assertThat(defaults.get(0)).contains("{}");
    }

    @Test
    @DisplayName("AC-AI-TAG-014: qna.tags 컬럼이 빈 배열 기본값으로 존재한다")
    void qnaTagsColumnExists() {
        List<String> defaults = jdbcTemplate.queryForList(
                "SELECT column_default FROM information_schema.columns " +
                        "WHERE table_name = 'qna' AND column_name = 'tags'", String.class);
        assertThat(defaults).hasSize(1);
        assertThat(defaults.get(0)).contains("{}");
    }

    @Test
    @DisplayName("AC-AI-TAG-014: tags 미전송 bbs_post 행은 빈 배열을 기본값으로 갖는다 (기존 INSERT 무파손)")
    void existingBbsPostRowHasEmptyTags() {
        Long bbsId = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_master ORDER BY id LIMIT 1", Long.class);
        // tags 컬럼을 전송하지 않는 기존 형태 INSERT — DEFAULT '{}'가 채워져야 한다
        jdbcTemplate.update(
                "INSERT INTO bbs_post (bbs_id, title, content_html, content_text, status, " +
                        "created_at, updated_at) " +
                        "VALUES (?, 'V54태그테스트', '<p>본문</p>', '본문', 'PUBLISHED', now(), now())",
                bbsId);
        Long postId = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_post WHERE title = 'V54태그테스트' ORDER BY id DESC LIMIT 1",
                Long.class);

        Integer emptyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bbs_post WHERE id = ? AND cardinality(tags) = 0",
                Integer.class, postId);
        assertThat(emptyCount).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM bbs_post WHERE id = ?", postId);
    }
}
