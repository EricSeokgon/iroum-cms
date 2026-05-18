package kr.co.ircp.cms.domain.policy.aimatch;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.infra.ml.MockMlServiceClient;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-AI-002 — 하이브리드 정책 추천 컨트롤러 IT.
 *
 * <p>AC-PM-001/003/004/005/008/009 — MockMlServiceClient 기반, 실제 PostgreSQL DB로
 * 추천 로그 적재까지 검증. aiLogExecutor는 {@link AiMatchItTestConfig}에서 동기화한다.
 */
// @MX:SPEC: SPEC-CMS-AI-002
@AutoConfigureMockMvc
@Import(AiMatchItTestConfig.class)
@DisplayName("정책 추천 컨트롤러 IT (SPEC-CMS-AI-002)")
class PolicyMatchControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMlServiceClient mlClient;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("DELETE FROM ai_policy_recommendation_log");
        jdbcTemplate.update("DELETE FROM policy_match_score");
        jdbcTemplate.update("DELETE FROM company_match_input");
        jdbcTemplate.update("DELETE FROM policy_program");
        mlClient.simulateTimeout(false);
        mlClient.resetPolicyMatchCallCount();
        mlClient.setFixedSemanticScores(null);
    }

    private long insertActivePolicy(String name) {
        String code = "PM-" + name + "-" + suffix;
        jdbcTemplate.update(
                "INSERT INTO policy_program (code, ministry, program_name, status) " +
                        "VALUES (?, '중기부', ?, 'ACTIVE')", code, name);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_program WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    @Test
    @DisplayName("AC-PM-001: 프로필+질의 추천 → hybrid 내림차순 Top-K, 각 항목 ruleScore/semanticScore/hybridScore")
    void hybridRecommendation() throws Exception {
        insertActivePolicy("정책A");
        insertActivePolicy("정책B");

        String body = """
                { "companyProfile": {"ksic_code":"62010","employee_count":10},
                  "queryText": "AI 지원", "topK": 5 }
                """;

        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("X-Session-Ref", "anon-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.items[0].hybridScore").exists())
                .andExpect(jsonPath("$.items[0].ruleScore").exists())
                .andExpect(jsonPath("$.items[0].semanticScore").exists());
    }

    @Test
    @DisplayName("AC-PM-008: 각 추천 항목에 ruleBreakdown + matchedTerms 설명이 포함된다")
    void explanationIncluded() throws Exception {
        insertActivePolicy("정책설명");

        String body = """
                { "companyProfile": {"ksic_code":"47"}, "queryText": "창업", "topK": 3 }
                """;

        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("X-Session-Ref", "anon-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].explanation.ruleBreakdown").exists())
                .andExpect(jsonPath("$.items[0].explanation.matchedTerms").isArray())
                .andExpect(jsonPath("$.items[0].explanation.semanticAvailable").value(true));
    }

    @Test
    @DisplayName("AC-PM-009: ML 폴백 → 200 + degraded=true + 설명 semanticAvailable=false")
    void fallbackWhenMlFails() throws Exception {
        insertActivePolicy("폴백정책");
        mlClient.simulateTimeout(true);

        String body = """
                { "companyProfile": {"ksic_code":"10"}, "topK": 5 }
                """;

        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("X-Session-Ref", "anon-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.items[0].explanation.semanticAvailable").value(false));
    }

    @Test
    @DisplayName("AC-PM-004: 추천 응답 후 ai_policy_recommendation_log에 VIEWED 행(policy_id=NULL) 적재")
    void viewedLogWritten() throws Exception {
        insertActivePolicy("로그정책");

        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("X-Session-Ref", "log-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"companyProfile\": {\"ksic_code\":\"62\"}, \"topK\": 3 }"))
                .andExpect(status().isOk());

        Integer viewedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_policy_recommendation_log " +
                        "WHERE interaction_type = 'VIEWED' AND policy_id IS NULL", Integer.class);
        assertThat(viewedRows).isEqualTo(1);

        // AC-PM-012: 평문 세션 토큰 미저장, session_ref 64 hex
        String sessionRef = jdbcTemplate.queryForObject(
                "SELECT session_ref FROM ai_policy_recommendation_log LIMIT 1", String.class);
        assertThat(sessionRef).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("AC-PM-003: 동일 입력 2회 호출 시 2번째는 MockMlServiceClient 미호출 (캐시 hit)")
    void cacheHitOnSecondCall() throws Exception {
        insertActivePolicy("캐시정책");
        String body = "{ \"companyProfile\": {\"ksic_code\":\"99\"}, \"queryText\": \"q\", \"topK\": 5 }";

        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("X-Session-Ref", "cache-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        int afterFirst = mlClient.policyMatchCallCount();

        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("X-Session-Ref", "cache-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        int afterSecond = mlClient.policyMatchCallCount();

        assertThat(afterFirst).isEqualTo(1);
        assertThat(afterSecond).isEqualTo(1); // 2번째는 캐시 hit — ML 미호출
    }

    @Test
    @DisplayName("AC-PM-005: 인증 회원 요청 시 본문 프로필 무시, DB CompanyMatchInput 사용")
    void authenticatedMemberUsesDbProfile() throws Exception {
        long companyId = insertUser("ai-member-" + suffix);
        // 회원의 DB 프로필 등록
        jdbcTemplate.update(
                "INSERT INTO company_match_input (company_id, industry_codes, region_codes, " +
                        "employee_count, custom_attrs) VALUES (?, ?, ?, ?, '{}'::jsonb)",
                companyId, new String[]{"IT"}, new String[]{"SEOUL"}, 25);
        insertActivePolicy("회원정책");

        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                companyId, "ai-member", Set.of("USER"), Set.of(),
                java.time.Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));

        // 본문에 위장 프로필 전달 → 무시되고 DB 프로필 기반 추천이어야 함 (200)
        mockMvc.perform(post("/api/v1/ai/policy-match")
                        .header("Authorization", "Bearer member-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"companyProfile\": {\"ksic_code\":\"FAKE\"}, \"topK\": 5 }"))
                .andExpect(status().isOk());
    }

    /** 테스트용 사용자 INSERT (다른 IT 와 충돌하지 않도록 unique username). */
    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', 'AI추천테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }
}
