package kr.co.ircp.cms.domain.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import org.springframework.test.web.servlet.MvcResult;

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
 * SPEC-CMS-AI-003 — RAG 질의응답 컨트롤러 IT.
 *
 * <p>AC-RAG-001/002/003/004/005/007/008/009 — MockMlServiceClient 기반, 실제
 * PostgreSQL DB로 질의 로그 적재까지 검증. aiLogExecutor는 {@link RagItTestConfig}에서 동기화.
 */
// @MX:SPEC: SPEC-CMS-AI-003
@AutoConfigureMockMvc
@Import(RagItTestConfig.class)
@DisplayName("RAG 질의응답 컨트롤러 IT (SPEC-CMS-AI-003)")
class RagQueryControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMlServiceClient mlClient;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private static final ObjectMapper JSON = new ObjectMapper();
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("DELETE FROM ai_rag_query_log");
        jdbcTemplate.update("DELETE FROM policy_program");
        mlClient.simulateTimeout(false);
        mlClient.resetRagCounters();
        // CircuitBreaker 강제 CLOSED 복구 (테스트 간 격리)
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("ml-service");
        cb.reset();
    }

    /** embed_vector가 채워진 활성 정책 INSERT (pgvector cosine 검색 대상). */
    private long insertEmbeddedPolicy(String name) {
        String code = "RAG-" + name + "-" + suffix;
        String vec = vectorLiteral();
        jdbcTemplate.update(
                "INSERT INTO policy_program (code, ministry, program_name, status, " +
                        "description_html, embed_vector, embedded_at, embed_model_version) " +
                        "VALUES (?, '중기부', ?, 'ACTIVE', ?, CAST(? AS vector), now(), 'mock-1.0')",
                code, name, name + " 상세 설명 청년 창업 지원", vec);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_program WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    /** embed_vector 없는 활성 정책 (FTS만 매칭, pgvector 미대상). */
    private long insertFtsOnlyPolicy(String name) {
        String code = "FTS-" + name + "-" + suffix;
        jdbcTemplate.update(
                "INSERT INTO policy_program (code, ministry, program_name, status, description_html) " +
                        "VALUES (?, '중기부', ?, 'ACTIVE', ?)",
                code, name, name + " 키워드 검색 본문");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_program WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private static String vectorLiteral() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 384; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(0.01 * (i % 7));
        }
        return sb.append(']').toString();
    }

    @Test
    @DisplayName("AC-RAG-001: 정상 질의 → 200 + answer/sources/degraded=false/queryRef, 로그 적재")
    void normalQuery() throws Exception {
        insertEmbeddedPolicy("청년창업A");
        insertEmbeddedPolicy("청년창업B");
        insertEmbeddedPolicy("청년창업C");

        MvcResult res = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"청년 창업 지원 정책 알려줘\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.queryRef").isNotEmpty())
                .andReturn();
        String queryRef = queryRefOf(res);

        // 본 테스트가 생성한 query_ref 로만 스코프 (공유 컨테이너 격리)
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_rag_query_log WHERE query_ref = ? AND degraded = false",
                Integer.class, queryRef);
        assertThat(rows).isEqualTo(1);
        Integer maxK = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(retrieved_policy_ids) FROM ai_rag_query_log " +
                        "WHERE query_ref = ?", Integer.class, queryRef);
        assertThat(maxK).isLessThanOrEqualTo(10);
    }

    /** 응답 JSON에서 queryRef 추출 (로그 행 스코프 키). */
    private String queryRefOf(MvcResult res) throws Exception {
        return JSON.readTree(res.getResponse().getContentAsString())
                .get("queryRef").asText();
    }

    @Test
    @DisplayName("AC-RAG-002: CircuitBreaker OPEN → 200(503 아님) + degraded=true + 캐시 미저장")
    void circuitBreakerFallback() throws Exception {
        insertFtsOnlyPolicy("창업지원폴백");
        mlClient.simulateTimeout(true);

        MvcResult res = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-cb-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"창업지원폴백 정책\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andReturn();

        Integer degradedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_rag_query_log WHERE query_ref = ? AND degraded = true",
                Integer.class, queryRefOf(res));
        assertThat(degradedRows).isEqualTo(1);

        // 캐시 미저장 → 동일 질문 재요청도 cached=false
        mlClient.simulateTimeout(true);
        mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-cb-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"창업지원폴백 정책\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.degraded").value(true));
    }

    @Test
    @DisplayName("AC-RAG-003: 동일 질문 캐시 히트 → cached=true, ML 미호출, 동일 본문")
    void cacheHit() throws Exception {
        insertEmbeddedPolicy("캐시정책");
        String body = "{ \"question\": \"캐시 테스트 질문 청년 창업\" }";

        MvcResult first = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-cache-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false))
                .andReturn();
        int embedAfterFirst = mlClient.embedCallCount();
        JsonNode firstJson = JSON.readTree(first.getResponse().getContentAsString());

        MvcResult second = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-cache-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andReturn();
        JsonNode secondJson = JSON.readTree(second.getResponse().getContentAsString());

        assertThat(mlClient.embedCallCount()).isEqualTo(embedAfterFirst); // 추가 ML 호출 없음
        assertThat(secondJson.get("answer")).isEqualTo(firstJson.get("answer"));
        assertThat(secondJson.get("sources")).isEqualTo(firstJson.get("sources"));
    }

    @Test
    @DisplayName("AC-RAG-004: 피드백 HELPFUL → feedback·feedback_at 갱신, 멱등, 잘못된 값 400")
    void feedbackLifecycle() throws Exception {
        insertEmbeddedPolicy("피드백정책");
        MvcResult q = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-fb-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"피드백 테스트 청년 창업\" }"))
                .andExpect(status().isOk()).andReturn();
        String queryRef = JSON.readTree(q.getResponse().getContentAsString())
                .get("queryRef").asText();

        mockMvc.perform(post("/api/v1/ai/rag/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"queryRef\": \"" + queryRef + "\", \"feedback\": \"HELPFUL\" }"))
                .andExpect(status().isNoContent());

        String fb = jdbcTemplate.queryForObject(
                "SELECT feedback FROM ai_rag_query_log WHERE query_ref = ?", String.class, queryRef);
        assertThat(fb).isEqualTo("HELPFUL");
        Integer fbAtNotNull = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_rag_query_log WHERE query_ref = ? AND feedback_at IS NOT NULL",
                Integer.class, queryRef);
        assertThat(fbAtNotNull).isEqualTo(1);

        // 멱등: UNHELPFUL 재제출 → 새 행 없이 갱신
        mockMvc.perform(post("/api/v1/ai/rag/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"queryRef\": \"" + queryRef + "\", \"feedback\": \"UNHELPFUL\" }"))
                .andExpect(status().isNoContent());
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_rag_query_log WHERE query_ref = ?", Integer.class, queryRef);
        assertThat(rowCount).isEqualTo(1);
        String fb2 = jdbcTemplate.queryForObject(
                "SELECT feedback FROM ai_rag_query_log WHERE query_ref = ?", String.class, queryRef);
        assertThat(fb2).isEqualTo("UNHELPFUL");

        // 잘못된 값 → 400
        mockMvc.perform(post("/api/v1/ai/rag/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"queryRef\": \"" + queryRef + "\", \"feedback\": \"MAYBE\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-RAG-005: 회원 질의 → 로그 question_hash/session_ref SHA-256, 질문 평문 미저장")
    void piiNotStored() throws Exception {
        long companyId = insertUser("rag-member-" + suffix);
        insertEmbeddedPolicy("회원질의정책");

        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                companyId, "rag-member", Set.of("USER"), Set.of(),
                java.time.Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));

        String question = "민감한 질문 청년 창업 지원";
        MvcResult res = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("Authorization", "Bearer member-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"" + question + "\" }"))
                .andExpect(status().isOk())
                .andReturn();
        String queryRef = queryRefOf(res);

        // question_hash 는 SHA-256 hex (64자), 평문 질문 미저장
        String qHash = jdbcTemplate.queryForObject(
                "SELECT question_hash FROM ai_rag_query_log WHERE query_ref = ?",
                String.class, queryRef);
        assertThat(qHash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(qHash).doesNotContain("청년", "민감");

        String sessionRef = jdbcTemplate.queryForObject(
                "SELECT session_ref FROM ai_rag_query_log WHERE query_ref = ?",
                String.class, queryRef);
        assertThat(sessionRef).hasSize(64).matches("[0-9a-f]{64}");

        // 질문 평문이 DB 어디에도 없음 (테이블에 question 컬럼 자체가 없음)
        Integer plainCols = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_name = 'ai_rag_query_log' AND column_name = 'question'",
                Integer.class);
        assertThat(plainCols).isZero();
    }

    @Test
    @DisplayName("AC-RAG-007: embed 단계만 실패 → 200, FTS 폴백, degraded=true, 캐시 미저장")
    void embedFailureFallback() throws Exception {
        insertFtsOnlyPolicy("임베딩실패폴백");
        mlClient.simulateEmbedFailure(true);

        MvcResult res = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-ef-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"임베딩실패폴백 정책\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andReturn();

        Integer degradedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_rag_query_log WHERE query_ref = ? AND degraded = true",
                Integer.class, queryRefOf(res));
        assertThat(degradedRows).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-RAG-008: 검색 0건 → 200, 빈 sources, 안내 메시지, 에러 미발생")
    void emptyResults() throws Exception {
        // 매칭 안 되는 정책만 존재
        insertFtsOnlyPolicy("전혀다른주제");

        MvcResult res = mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-empty-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"존재하지않는질문ZZZ\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andReturn();

        Integer emptyIds = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_rag_query_log " +
                        "WHERE query_ref = ? AND jsonb_array_length(retrieved_policy_ids) = 0",
                Integer.class, queryRefOf(res));
        assertThat(emptyIds).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-RAG-009: 빈 질문 → 400")
    void blankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-blank-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"   \" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-RAG-009: 1000자 초과 질문 → 400")
    void tooLongQuestion() throws Exception {
        String longQ = "가".repeat(1001);
        mockMvc.perform(post("/api/v1/ai/rag/query")
                        .header("X-Session-Ref", "anon-long-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"" + longQ + "\" }"))
                .andExpect(status().isBadRequest());
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', 'RAG테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }
}
