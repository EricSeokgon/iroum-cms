package kr.co.ircp.cms.domain.ai.tag;

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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 * SPEC-CMS-AI-004 — 태그 추천 컨트롤러 IT.
 *
 * <p>AC-AI-TAG-006/007/008/009/012 — MockMlServiceClient 기반, 실제 PostgreSQL로
 * 추천/피드백 로그 적재까지 검증. aiLogExecutor는 {@link TagRecommendationItTestConfig}에서 동기화.
 */
// @MX:SPEC: SPEC-CMS-AI-004
@AutoConfigureMockMvc
@Import(TagRecommendationItTestConfig.class)
@DisplayName("태그 추천 컨트롤러 IT (SPEC-CMS-AI-004)")
class TagRecommendationControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMlServiceClient mlClient;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired CacheManager cacheManager;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private String suffix;
    // 20자 이상 본문 (최소 길이 가드 통과)
    private static final String LONG_CONTENT = "스마트팜 청년 창업 지원 정책 본문 내용 충분히 긴 문장입니다";
    // 19자 본문 (경계 — 미만)
    private static final String SHORT_CONTENT = "열아홉자본문열아홉자본문열아홉자가";

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("DELETE FROM ai_tag_recommendation_log");
        mlClient.simulateTimeout(false);
        mlClient.resetTagRecommendationCounters();
        // 캐시는 애플리케이션 스코프이므로 테스트 간 격리를 위해 비운다
        // (동일 본문 재사용 시 캐시 히트로 logSuggested가 스킵되는 것을 방지)
        Cache cache = cacheManager.getCache("tagRecommendationCache");
        if (cache != null) {
            cache.clear();
        }
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("ml-service");
        cb.reset();
    }

    @Test
    @DisplayName("AC-AI-TAG-006: 관리자 인증 + 20자 이상 본문 → 200 + 추천 태그 (<=5)")
    void adminRecommend() throws Exception {
        long userId = insertUser("tag-admin-" + suffix);
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "tag-admin", Set.of("ADMIN"), Set.of(),
                java.time.Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));

        mockMvc.perform(post("/api/v1/ai/tag-recommend")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"" + LONG_CONTENT + "\", \"contentType\": \"POST\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedTags").isArray());
    }

    @Test
    @DisplayName("AC-AI-TAG-007: 시민 비인증 + 20자 이상 본문 → 200 (화이트리스트)")
    void citizenRecommendUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tag-recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"" + LONG_CONTENT + "\", \"contentType\": \"QNA\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedTags").isArray());
    }

    @Test
    @DisplayName("AC-AI-TAG-008: 비인증 + 19자 본문 → 200 + 빈 배열, ML 미호출")
    void shortContentReturnsEmpty() throws Exception {
        assertThat(SHORT_CONTENT.length()).isLessThan(20);

        mockMvc.perform(post("/api/v1/ai/tag-recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"" + SHORT_CONTENT + "\", \"contentType\": \"QNA\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedTags").isEmpty());

        assertThat(mlClient.tagRecommendationCallCount()).isZero();
    }

    @Test
    @DisplayName("AC-AI-TAG-009: 비인증 + ML 다운 → 200 + 빈 배열 (그레이스풀 폴백)")
    void mlDownReturnsEmpty() throws Exception {
        mlClient.simulateTagRecommendationTimeout(true);

        mockMvc.perform(post("/api/v1/ai/tag-recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"" + LONG_CONTENT + "\", \"contentType\": \"QNA\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedTags").isEmpty());
    }

    @Test
    @DisplayName("AC-AI-TAG-011: 추천 성공 시 SUGGESTED 로그 행이 적재된다")
    void suggestedLogPersisted() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tag-recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"" + LONG_CONTENT + "\", \"contentType\": \"QNA\" }"))
                .andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_tag_recommendation_log WHERE event_type = 'SUGGESTED'",
                Integer.class);
        assertThat(rows).isGreaterThanOrEqualTo(1);

        // 세션 식별은 SHA-256 해시 (평문 미저장)
        String sessionRef = jdbcTemplate.queryForObject(
                "SELECT session_ref FROM ai_tag_recommendation_log " +
                        "WHERE event_type = 'SUGGESTED' ORDER BY id DESC LIMIT 1", String.class);
        assertThat(sessionRef).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("AC-AI-TAG-012: 채택 피드백 → 200 + ACCEPTED 로그 행 (tag_value 포함)")
    void feedbackAcceptedPersisted() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tag-recommend/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"" + LONG_CONTENT + "\", \"contentType\": \"QNA\", " +
                                "\"eventType\": \"ACCEPTED\", \"tagValue\": \"태그1\" }"))
                .andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_tag_recommendation_log " +
                        "WHERE event_type = 'ACCEPTED' AND tag_value = '태그1'", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '태그테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }
}
