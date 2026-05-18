package kr.co.ircp.cms.domain.ai.rag;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-AI-003 — RAG 품질 모니터링 운영자 컨트롤러 IT.
 *
 * <p>AC-RAG-006 — 만족도·캐시 히트율·degraded 비율·시계열 집계, ROLE=ADMIN
 * 권한(비ADMIN 403) + SPEC-CMS-005 audit_log 적재.
 */
// @MX:SPEC: SPEC-CMS-AI-003
@AutoConfigureMockMvc
@DisplayName("RAG 모니터링 컨트롤러 IT (SPEC-CMS-AI-003)")
class RagAdminControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private String suffix;
    private long adminId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("DELETE FROM ai_rag_query_log");
        adminId = insertUser("ragmetrics-admin-" + suffix);
    }

    private void seedQuery(String feedback, boolean cacheHit, boolean degraded, int latencyMs) {
        // feedback_at: feedback 유무에 따라 NULL 또는 now() — JDBC가 null Instant 타입을
        // 추론하지 못하므로 SQL 측 분기로 처리(chk_arql_feedback_pair 제약 정합).
        jdbcTemplate.update(
                "INSERT INTO ai_rag_query_log " +
                        "(query_ref, question_hash, session_ref, retrieved_policy_ids, " +
                        " latency_ms, cache_hit, degraded, feedback, feedback_at) " +
                        "VALUES (?, ?, ?, '[1,2]'::jsonb, ?, ?, ?, ?, " +
                        " CASE WHEN ?::text IS NULL THEN NULL ELSE now() END)",
                UUID.randomUUID().toString(),
                "h-" + UUID.randomUUID().toString().replace("-", ""),
                "s-" + suffix, latencyMs, cacheHit, degraded,
                feedback, feedback);
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "ragmetrics-admin", Set.of("ADMIN"), Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private void givenUserToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "plain-user", Set.of("USER"), Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    @Test
    @DisplayName("AC-RAG-006: ADMIN 메트릭 조회 → 200 + 만족도/캐시히트율/degraded 비율 + audit_log")
    void adminMetrics() throws Exception {
        // 3 HELPFUL, 1 UNHELPFUL → satisfaction 0.75
        seedQuery("HELPFUL", false, false, 100);
        seedQuery("HELPFUL", false, false, 200);
        seedQuery("HELPFUL", true, false, 50);
        seedQuery("UNHELPFUL", false, true, 300);
        seedQuery(null, true, false, 80); // 피드백 없음 + 캐시 히트
        givenAdminToken();

        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'RagMetrics'", Integer.class);

        mockMvc.perform(get("/api/v1/admin/ai/rag/metrics")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satisfactionRate").value(
                        org.hamcrest.Matchers.closeTo(0.75, 0.01)))
                .andExpect(jsonPath("$.totalQueries").value(5))
                .andExpect(jsonPath("$.cacheHitRate").value(
                        org.hamcrest.Matchers.closeTo(0.4, 0.01)))
                .andExpect(jsonPath("$.degradedRate").value(
                        org.hamcrest.Matchers.closeTo(0.2, 0.01)))
                .andExpect(jsonPath("$.avgLatencyMs").value(
                        org.hamcrest.Matchers.closeTo(146.0, 1.0)))
                .andExpect(jsonPath("$.timeSeries").isArray());

        Integer after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'RagMetrics'", Integer.class);
        assertThat(after).isGreaterThan(before == null ? 0 : before);
    }

    @Test
    @DisplayName("AC-RAG-006: 비ADMIN(USER) 메트릭 호출 → 403, 본문 미제공")
    void nonAdminForbidden() throws Exception {
        givenUserToken();

        mockMvc.perform(get("/api/v1/admin/ai/rag/metrics")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-RAG-006: 비회원(미인증) 메트릭 호출 → 401/403, 본문 미제공")
    void anonymousDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/rag/metrics"))
                .andExpect(status().is4xxClientError());
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', 'RAG지표테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }
}
