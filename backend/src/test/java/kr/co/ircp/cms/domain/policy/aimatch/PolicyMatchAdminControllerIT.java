package kr.co.ircp.cms.domain.policy.aimatch;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-AI-002 — 추천 품질 모니터링 운영자 컨트롤러 IT.
 *
 * <p>AC-PM-013/014/015 — CTR·커버리지 집계, ADMIN 권한 + audit_log 적재.
 */
// @MX:SPEC: SPEC-CMS-AI-002
@AutoConfigureMockMvc
@DisplayName("정책 추천 모니터링 컨트롤러 IT (SPEC-CMS-AI-002)")
class PolicyMatchAdminControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private String suffix;
    private long adminId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("DELETE FROM ai_policy_recommendation_log");
        jdbcTemplate.update("DELETE FROM policy_program");
        adminId = insertUser("aimetrics-admin-" + suffix);
    }

    private void seedViewed(String session, String policyIdsJson) {
        jdbcTemplate.update(
                "INSERT INTO ai_policy_recommendation_log " +
                        "(session_ref, company_profile, recommended_policy_ids, interaction_type) " +
                        "VALUES (?, '{}'::jsonb, ?::jsonb, 'VIEWED')", session, policyIdsJson);
    }

    private void seedFeedback(String session, String type, long policyId) {
        jdbcTemplate.update(
                "INSERT INTO ai_policy_recommendation_log " +
                        "(session_ref, company_profile, interaction_type, policy_id, interacted_at) " +
                        "VALUES (?, '{}'::jsonb, ?, ?, now())", session, type, policyId);
    }

    private long insertActivePolicy(String name) {
        String code = "AM-" + name + "-" + suffix;
        jdbcTemplate.update(
                "INSERT INTO policy_program (code, ministry, program_name, status) " +
                        "VALUES (?, '중기부', ?, 'ACTIVE')", code, name);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_program WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "aimetrics-admin", Set.of("ADMIN"), Set.of(),
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
    @DisplayName("AC-PM-013: 3 VIEWED 세션 중 1 CLICKED → CTR ≈ 0.333")
    void ctrAggregation() throws Exception {
        seedViewed("s1-" + suffix, "[1,2]");
        seedViewed("s2-" + suffix, "[1,3]");
        seedViewed("s3-" + suffix, "[2,4]");
        seedFeedback("s1-" + suffix, "CLICKED", 1L);
        givenAdminToken();

        mockMvc.perform(get("/api/v1/admin/ai/policy-match/metrics")
                        .header("Authorization", "Bearer admin-token")
                        .param("period", "DAILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViewed").value(3))
                .andExpect(jsonPath("$.totalClicked").value(1))
                .andExpect(jsonPath("$.ctr").value(org.hamcrest.Matchers.closeTo(0.333, 0.01)));
    }

    @Test
    @DisplayName("AC-PM-014: 활성 정책 10개 중 추천에 4개 등장 → coverage = 0.4")
    void coverageAggregation() throws Exception {
        for (int i = 0; i < 10; i++) {
            insertActivePolicy("P" + i);
        }
        // 추천에 정책 4개(101,102,103,104) 등장
        seedViewed("c1-" + suffix, "[101,102]");
        seedViewed("c2-" + suffix, "[103,104]");
        givenAdminToken();

        mockMvc.perform(get("/api/v1/admin/ai/policy-match/metrics")
                        .header("Authorization", "Bearer admin-token")
                        .param("period", "DAILY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage").value(org.hamcrest.Matchers.closeTo(0.4, 0.001)));
    }

    @Test
    @DisplayName("AC-PM-015: 비ADMIN 모니터링 호출 → 403")
    void nonAdminForbidden() throws Exception {
        givenUserToken();

        mockMvc.perform(get("/api/v1/admin/ai/policy-match/metrics")
                        .header("Authorization", "Bearer user-token")
                        .param("period", "DAILY"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-PM-015: ADMIN 호출 → 200 + audit_log 1건 적재")
    void adminCallAudited() throws Exception {
        givenAdminToken();
        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'PolicyMatchMetrics'",
                Integer.class);

        mockMvc.perform(get("/api/v1/admin/ai/policy-match/metrics")
                        .header("Authorization", "Bearer admin-token")
                        .param("period", "DAILY"))
                .andExpect(status().isOk());

        Integer after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'PolicyMatchMetrics'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(after)
                .isGreaterThan(before == null ? 0 : before);
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', 'AI지표테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }
}
