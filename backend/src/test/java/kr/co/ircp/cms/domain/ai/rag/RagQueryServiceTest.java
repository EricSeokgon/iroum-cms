package kr.co.ircp.cms.domain.ai.rag;

import com.github.benmanes.caffeine.cache.Caffeine;
import kr.co.ircp.cms.domain.ai.rag.config.RagProperties;
import kr.co.ircp.cms.domain.ai.rag.dto.RagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryResponse;
import kr.co.ircp.cms.domain.ai.rag.repository.PolicyEmbeddingRepository;
import kr.co.ircp.cms.domain.ai.rag.repository.RagQueryLogRepository;
import kr.co.ircp.cms.domain.ai.rag.service.RagQueryLogService;
import kr.co.ircp.cms.domain.ai.rag.service.RagQueryServiceImpl;
import kr.co.ircp.cms.domain.search.dto.DocResult;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.service.SearchService;
import kr.co.ircp.cms.infra.ml.MockMlServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-AI-003 — RAG 오케스트레이션 서비스 단위 테스트.
 *
 * <p>RED→GREEN: 입력 검증·캐시 히트·CircuitBreaker 폴백·임베딩 실패 폴백·
 * 하이브리드 재랭킹·빈 결과·피드백 검증을 MockMlServiceClient + mock 의존으로 검증.
 */
// @MX:SPEC: SPEC-CMS-AI-003
@DisplayName("RAG 오케스트레이션 서비스 단위 (SPEC-CMS-AI-003)")
class RagQueryServiceTest {

    private MockMlServiceClient mlClient;
    private PolicyEmbeddingRepository embeddingRepo;
    private SearchService searchService;
    private RagQueryLogService logService;
    private RagQueryLogRepository logRepo;
    private SimpleCacheManager cacheManager;
    private RagProperties props;
    private RagQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        mlClient = new MockMlServiceClient();
        embeddingRepo = mock(PolicyEmbeddingRepository.class);
        searchService = mock(SearchService.class);
        logService = mock(RagQueryLogService.class);
        logRepo = mock(RagQueryLogRepository.class);
        props = new RagProperties();

        cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(new CaffeineCache(
                "ragQueryCache", Caffeine.newBuilder().build())));
        cacheManager.afterPropertiesSet();

        service = new RagQueryServiceImpl(
                mlClient, embeddingRepo, searchService, logService, logRepo,
                cacheManager, props);
    }

    private void givenPgVectorPolicies() {
        when(embeddingRepo.searchByCosine(anyString(), anyInt())).thenReturn(List.of(
                Map.of("id", 101L, "title", "청년 창업 지원", "content", "청년 창업 자금 지원", "score", 0.91),
                Map.of("id", 102L, "title", "소상공인 융자", "content", "소상공인 저리 융자", "score", 0.72)));
    }

    private void givenFtsPolicies() {
        when(searchService.search(any(), any(), anyBoolean(), any(), any())).thenReturn(
                new SearchResponse(null, 2, 1, List.of(
                        new DocResult("policy", 101L, "청년 창업 지원", "snippet", null, 0.8,
                                "policy", "/policy/101", Instant.now()),
                        new DocResult("policy", 203L, "예비창업패키지", "snippet", null, 0.6,
                                "policy", "/policy/203", Instant.now())),
                        Map.of(), "청년 창업"));
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    @Test
    @DisplayName("AC-RAG-001: 정상 질의 → answer+sources+degraded=false, K 상한 이하, 로그 적재")
    void normalQuery() {
        givenPgVectorPolicies();
        givenFtsPolicies();

        RagQueryResponse resp = service.query(
                new RagQueryRequest("청년 창업 지원 정책 알려줘"), "anon-1", null);

        assertThat(resp.answer()).isNotBlank();
        assertThat(resp.sources()).isNotEmpty();
        assertThat(resp.sources().size()).isLessThanOrEqualTo(props.getTopKMax());
        assertThat(resp.degraded()).isFalse();
        assertThat(resp.cached()).isFalse();
        assertThat(resp.queryRef()).isNotBlank();
        verify(logService).logQueryAsync(any());
    }

    @Test
    @DisplayName("AC-RAG-002: CircuitBreaker OPEN → degraded=true, FTS 결과, 캐시 미저장")
    void circuitBreakerFallback() {
        givenPgVectorPolicies();
        givenFtsPolicies();
        mlClient.simulateTimeout(true); // embed/rag 모두 실패

        RagQueryResponse resp = service.query(
                new RagQueryRequest("창업 지원"), "anon-2", null);

        assertThat(resp.degraded()).isTrue();
        assertThat(resp.sources()).isNotEmpty(); // FTS 결과
        // degraded → 캐시 미저장: 동일 질문 재요청도 degraded (캐시 hit 아님)
        RagQueryResponse again = service.query(
                new RagQueryRequest("창업 지원"), "anon-2", null);
        assertThat(again.cached()).isFalse();
    }

    @Test
    @DisplayName("AC-RAG-007: embed 단계만 실패 → pgvector 스킵, FTS 폴백, degraded=true")
    void embedFailureFallback() {
        givenFtsPolicies();
        mlClient.simulateEmbedFailure(true);

        RagQueryResponse resp = service.query(
                new RagQueryRequest("창업 자금"), "anon-3", null);

        assertThat(resp.degraded()).isTrue();
        assertThat(resp.sources()).isNotEmpty();
        verify(embeddingRepo, never()).searchByCosine(anyString(), anyInt());
    }

    @Test
    @DisplayName("AC-RAG-003: 동일 질문 2회 → 2번째 캐시 히트, ML 미호출, 동일 본문")
    void cacheHit() {
        givenPgVectorPolicies();
        givenFtsPolicies();

        RagQueryResponse first = service.query(
                new RagQueryRequest("청년 창업"), "anon-4", null);
        int embedAfterFirst = mlClient.embedCallCount();

        RagQueryResponse second = service.query(
                new RagQueryRequest("청년 창업"), "anon-4", null);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(mlClient.embedCallCount()).isEqualTo(embedAfterFirst); // 추가 호출 없음
        assertThat(second.answer()).isEqualTo(first.answer());
        assertThat(second.sources()).isEqualTo(first.sources());
    }

    @Test
    @DisplayName("AC-RAG-008: pgvector·FTS 모두 0건 → 빈 sources, 안내 메시지, 에러 미발생")
    void emptyResults() {
        when(embeddingRepo.searchByCosine(anyString(), anyInt())).thenReturn(List.of());
        when(searchService.search(any(), any(), anyBoolean(), any(), any())).thenReturn(
                new SearchResponse(null, 0, 0, List.of(), Map.of(), "무관"));

        RagQueryResponse resp = service.query(
                new RagQueryRequest("존재하지 않는 정책 xyz"), "anon-5", null);

        assertThat(resp.sources()).isEmpty();
        assertThat(resp.answer()).isNotBlank(); // 안내 메시지 (환각 아님)
    }

    @Test
    @DisplayName("AC-RAG-009: 빈 질문 → 400(IllegalArgument)")
    void blankQuestionRejected() {
        assertThatThrownBy(() -> service.query(
                new RagQueryRequest("   "), "anon-6", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-RAG-009: 1000자 초과 질문 → 400(IllegalArgument)")
    void tooLongQuestionRejected() {
        String longQ = "가".repeat(1001);
        assertThatThrownBy(() -> service.query(
                new RagQueryRequest(longQ), "anon-7", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-RAG-006: 하이브리드 재랭킹 — vector·fts 가중 결합 점수 내림차순")
    void hybridReranking() {
        givenPgVectorPolicies();
        givenFtsPolicies();

        RagQueryResponse resp = service.query(
                new RagQueryRequest("청년 창업"), "anon-8", null);

        // 101은 vector(0.91)+fts(0.8) 둘 다 상위 → 최상위 출처
        assertThat(resp.sources().get(0).id()).isEqualTo(101L);
        // 관련도 내림차순
        for (int i = 1; i < resp.sources().size(); i++) {
            assertThat(resp.sources().get(i - 1).relevance())
                    .isGreaterThanOrEqualTo(resp.sources().get(i).relevance());
        }
    }

    @Test
    @DisplayName("AC-RAG-004: 잘못된 feedback 값 → 400(IllegalArgument)")
    void invalidFeedbackRejected() {
        assertThatThrownBy(() -> service.feedback(
                new RagFeedbackRequest("ref-1", "MAYBE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-RAG-004: HELPFUL 피드백 → queryRef로 갱신 위임")
    void validFeedbackDelegates() {
        when(logRepo.updateFeedback("ref-1", "HELPFUL")).thenReturn(1);

        service.feedback(new RagFeedbackRequest("ref-1", "HELPFUL"));

        verify(logRepo).updateFeedback("ref-1", "HELPFUL");
    }
}
