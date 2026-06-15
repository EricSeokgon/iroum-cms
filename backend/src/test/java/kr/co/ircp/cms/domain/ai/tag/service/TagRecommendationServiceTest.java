package kr.co.ircp.cms.domain.ai.tag.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import kr.co.ircp.cms.domain.ai.tag.dto.TagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagRecommendRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagRecommendResponse;
import kr.co.ircp.cms.infra.ml.MockMlServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SPEC-CMS-AI-004 — 태그 추천 오케스트레이션 서비스 단위 테스트 (RED, Docker 불필요).
 *
 * <p>최소 길이 가드·캐시 히트·ML 성공/장애 폴백·비동기 로깅·세션 해시를
 * MockMlServiceClient + mock 의존으로 검증한다(AC-AI-TAG-006/008/009/010/011/013).
 */
// @MX:SPEC: SPEC-CMS-AI-004
@DisplayName("태그 추천 오케스트레이션 서비스 단위 (SPEC-CMS-AI-004)")
class TagRecommendationServiceTest {

    private MockMlServiceClient mlClient;
    private AiTagRecommendationLogService logService;
    private SimpleCacheManager cacheManager;
    private TagRecommendationService service;

    private static final String LONG_CONTENT = "스마트팜 청년 창업 지원 정책 본문 내용입니다 충분히 김"; // 20자 이상

    @BeforeEach
    void setUp() {
        mlClient = new MockMlServiceClient();
        logService = mock(AiTagRecommendationLogService.class);
        cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(new CaffeineCache(
                "tagRecommendationCache", Caffeine.newBuilder().build())));
        cacheManager.afterPropertiesSet();
        service = new TagRecommendationService(mlClient, logService, cacheManager);
    }

    @Test
    @DisplayName("AC-AI-TAG-008: 본문 20자 미만 → ML 미호출 + 빈 배열")
    void shortContentReturnsEmpty() {
        TagRecommendResponse resp = service.recommendTags(
                new TagRecommendRequest("짧은 본문", List.of(), "POST"), "1.2.3.4");

        assertThat(resp.recommendedTags()).isEmpty();
        assertThat(mlClient.tagRecommendationCallCount()).isZero();
    }

    @Test
    @DisplayName("AC-AI-TAG-008: 빈 본문 → ML 미호출 + 빈 배열")
    void blankContentReturnsEmpty() {
        TagRecommendResponse resp = service.recommendTags(
                new TagRecommendRequest("", List.of(), "POST"), "1.2.3.4");

        assertThat(resp.recommendedTags()).isEmpty();
        assertThat(mlClient.tagRecommendationCallCount()).isZero();
    }

    @Test
    @DisplayName("AC-AI-TAG-006: 20자 이상 본문 → ML 호출, 최대 5개 추천 반환")
    void validContentReturnsRecommendations() {
        TagRecommendResponse resp = service.recommendTags(
                new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");

        assertThat(resp.recommendedTags()).isNotEmpty();
        assertThat(resp.recommendedTags()).hasSizeLessThanOrEqualTo(5);
        assertThat(mlClient.tagRecommendationCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-AI-TAG-010: 동일 본문 2회 → 캐시 히트, ML 1회만 호출")
    void cacheHitOnSameContent() {
        service.recommendTags(new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");
        service.recommendTags(new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");

        assertThat(mlClient.tagRecommendationCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-AI-TAG-009: ML 장애(MlServiceException) → 빈 배열 200, 오류 미전파")
    void mlFailureReturnsEmpty() {
        mlClient.simulateTagRecommendationTimeout(true);

        TagRecommendResponse resp = service.recommendTags(
                new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");

        assertThat(resp.recommendedTags()).isEmpty();
    }

    @Test
    @DisplayName("AC-AI-TAG-011: 추천 성공 시 logSuggested가 비동기 호출된다")
    void logsSuggestedAfterMlCall() {
        service.recommendTags(new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");

        verify(logService).logSuggested(anyString(), anyString(), anyString(),
                anyList(), anyMap(), any());
    }

    @Test
    @DisplayName("AC-AI-TAG-011: ML 장애 시 logSuggested는 호출되지 않는다")
    void noLogOnMlFailure() {
        mlClient.simulateTagRecommendationTimeout(true);

        service.recommendTags(new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");

        verify(logService, never()).logSuggested(anyString(), anyString(), anyString(),
                anyList(), anyMap(), any());
    }

    @Test
    @DisplayName("AC-AI-TAG-013: 세션 식별은 평문 IP가 아닌 SHA-256 해시로 로깅된다")
    void sessionUsesSha256Hash() {
        service.recommendTags(new TagRecommendRequest(LONG_CONTENT, List.of(), "POST"), "1.2.3.4");

        org.mockito.ArgumentCaptor<String> sessionCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logService).logSuggested(sessionCaptor.capture(), anyString(), anyString(),
                anyList(), anyMap(), any());
        // SHA-256 hex 64자, 평문 IP 미포함
        assertThat(sessionCaptor.getValue()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(sessionCaptor.getValue()).doesNotContain("1.2.3.4");
    }

    @Test
    @DisplayName("AC-AI-TAG-012: 채택 피드백 → logFeedback 위임 (ACCEPTED, tag_value)")
    void recordFeedbackDelegates() {
        service.recordFeedback(
                new TagFeedbackRequest(LONG_CONTENT, "POST", "ACCEPTED", "태그1"), "1.2.3.4");

        verify(logService).logFeedback(anyString(), org.mockito.ArgumentMatchers.eq("POST"),
                anyString(), org.mockito.ArgumentMatchers.eq("ACCEPTED"),
                org.mockito.ArgumentMatchers.eq("태그1"));
    }

    @Test
    @DisplayName("E6: 추천 결과에서 기존 선택 태그는 제외된다")
    void existingTagsExcluded() {
        TagRecommendResponse resp = service.recommendTags(
                new TagRecommendRequest(LONG_CONTENT, List.of("테스트태그"), "POST"), "1.2.3.4");

        assertThat(resp.recommendedTags()).doesNotContain("테스트태그");
    }
}
