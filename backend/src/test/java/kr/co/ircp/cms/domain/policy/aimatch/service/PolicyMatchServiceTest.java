package kr.co.ircp.cms.domain.policy.aimatch.service;

import kr.co.ircp.cms.domain.policy.aimatch.config.PolicyMatchProperties;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchItem;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.matching.dto.MatchedPolicy;
import kr.co.ircp.cms.domain.policy.matching.service.PolicyMatchingService;
import kr.co.ircp.cms.infra.ml.MockMlServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.support.NoOpCacheManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PolicyMatchService 단위 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-002 — 하이브리드 점수 결합·Top-K 클램프·ML 폴백.
 * MockMlServiceClient 기반으로 ML 모델 부재 시에도 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyMatchService — 하이브리드 머지 (SPEC-CMS-AI-002)")
class PolicyMatchServiceTest {

    @Mock
    private PolicyMatchingService policyMatchingService;

    @Mock
    private kr.co.ircp.cms.domain.policy.aimatch.repository.PolicyRecommendationLogMapper logMapper;

    private MockMlServiceClient mlClient;
    private PolicyRecommendationLogService logService;
    private PolicyMatchProperties properties;
    private PolicyMatchService service;

    @BeforeEach
    void setUp() {
        mlClient = new MockMlServiceClient();
        // 로그 서비스는 비동기 적재 — 단위 테스트에서는 동작만 호출되면 충분 (mapper mock)
        logService = new PolicyRecommendationLogService(logMapper);
        properties = new PolicyMatchProperties();
        service = new PolicyMatchService(
                policyMatchingService, mlClient, logService, properties,
                new NoOpCacheManager(), logMapper);
    }

    private MatchedPolicy ruleMatch(long policyId, String score, String breakdownJson) {
        return new MatchedPolicy(
                policyId, "정책-" + policyId, "중기부",
                Instant.now(), new BigDecimal(score), "A", breakdownJson, Instant.now());
    }

    /** 회원 컨텍스트 — companyId 기반 규칙 경로(matchForCompany) 활성화. */
    private org.springframework.security.core.Authentication memberAuth(long companyId) {
        kr.co.ircp.cms.domain.auth.security.JwtPrincipal principal =
                new kr.co.ircp.cms.domain.auth.security.JwtPrincipal(
                        companyId, "member", java.util.Set.of("USER"));
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of());
    }

    @Test
    @DisplayName("AC-PM-006: rule=80→0.8, semantic=0.5, 기본 가중치 → hybrid=0.62")
    void hybridScoreCombination() {
        // Given — 규칙 점수 80, 시맨틱 0.5 고정 (회원 컨텍스트 → 규칙 경로)
        when(policyMatchingService.matchForCompany(eq(7L), anyInt()))
                .thenReturn(new kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse(
                        7L, 50, false,
                        List.of(ruleMatch(101L, "80",
                                "{\"industry\":30,\"region\":20,\"size\":20,\"age\":10}"))));
        mlClient.setFixedSemanticScores(Map.of(101L, 0.5));

        // When
        PolicyMatchResponse resp = service.recommend(
                "raw-token", new PolicyMatchRequest(Map.of("ksic_code", "62010"), "AI 지원", null),
                memberAuth(7L));

        // Then — hybrid = 0.4*0.8 + 0.6*0.5 = 0.62 (±0.0001)
        assertThat(resp.degraded()).isFalse();
        assertThat(resp.items()).hasSize(1);
        PolicyMatchItem item = resp.items().get(0);
        assertThat(item.ruleScore()).isEqualTo(0.8);
        assertThat(item.semanticScore()).isEqualTo(0.5);
        assertThat(item.hybridScore()).isCloseTo(0.62,
                org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("AC-PM-006 산식: hybrid(80, 0.5) = 0.62 (직접 검증)")
    void hybridFormulaDirect() {
        assertThat(service.hybrid(80.0, 0.5))
                .isCloseTo(0.62, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("AC-PM-007: MockMlServiceClient 타임아웃 → 예외 미전파, degraded=true, 규칙 단독 랭킹")
    void mlFallbackOnTimeout() {
        // Given — ML 타임아웃, 규칙 점수 60 (회원 컨텍스트 → 규칙 경로)
        when(policyMatchingService.matchForCompany(eq(9L), anyInt()))
                .thenReturn(new kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse(
                        9L, 50, false,
                        List.of(ruleMatch(202L, "60", "{\"industry\":30,\"region\":15}"))));
        mlClient.simulateTimeout(true);

        // When — 예외가 전파되지 않아야 함
        PolicyMatchResponse resp = service.recommend(
                "raw-token", new PolicyMatchRequest(Map.of("ksic_code", "47"), null, 5),
                memberAuth(9L));

        // Then — degraded=true, 시맨틱 0, hybrid = rule_norm(0.6)
        assertThat(resp.degraded()).isTrue();
        assertThat(resp.items()).hasSize(1);
        PolicyMatchItem item = resp.items().get(0);
        assertThat(item.semanticScore()).isEqualTo(0.0);
        assertThat(item.hybridScore()).isCloseTo(0.6,
                org.assertj.core.data.Offset.offset(0.0001));
        assertThat(item.explanation().semanticAvailable()).isFalse();
    }

    @Test
    @DisplayName("AC-PM-002: top_k=0 → 기본 10, top_k=999 → 상한 50으로 클램프")
    void topKClamping() {
        assertThat(service.clampTopK(0)).isEqualTo(10);
        assertThat(service.clampTopK(-5)).isEqualTo(10);
        assertThat(service.clampTopK(null)).isEqualTo(10);
        assertThat(service.clampTopK(999)).isEqualTo(50);
        assertThat(service.clampTopK(7)).isEqualTo(7);
        assertThat(service.clampTopK(50)).isEqualTo(50);
    }

    @Test
    @DisplayName("AC-PM-001: 결과가 hybrid 점수 내림차순으로 정렬된다")
    void resultsSortedByHybridDesc() {
        when(policyMatchingService.matchForCompany(eq(3L), anyInt()))
                .thenReturn(new kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse(
                        3L, 50, false,
                        List.of(
                                ruleMatch(1L, "40", "{\"industry\":40}"),
                                ruleMatch(2L, "90", "{\"industry\":90}"),
                                ruleMatch(3L, "60", "{\"industry\":60}"))));
        mlClient.setFixedSemanticScores(Map.of(1L, 0.9, 2L, 0.2, 3L, 0.5));

        PolicyMatchResponse resp = service.recommend(
                "tok", new PolicyMatchRequest(Map.of("ksic_code", "10"), "q", 10), memberAuth(3L));

        assertThat(resp.items())
                .extracting(PolicyMatchItem::hybridScore)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
