package kr.co.ircp.cms.domain.policy.aimatch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.common.util.IpHashUtil;
import kr.co.ircp.cms.domain.policy.aimatch.config.PolicyMatchProperties;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchExplanation;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchItem;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.aimatch.repository.PolicyRecommendationLogMapper;
import kr.co.ircp.cms.domain.policy.matching.dto.MatchedPolicy;
import kr.co.ircp.cms.domain.policy.matching.exception.CompanyMatchInputNotFoundException;
import kr.co.ircp.cms.domain.policy.matching.service.PolicyMatchingService;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.MlMatchItem;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchRequest;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하이브리드 정책 추천 서비스.
 *
 * <p>SPEC-CMS-AI-002 — SPEC-CMS-007 규칙 점수(0~100 정규화)와 ML 시맨틱 점수(0~1)를
 * 설정 가중치로 결합한다. ML 장애 시 규칙 단독 폴백(REQ-PM-009).
 */
@Service
public class PolicyMatchService {

    private static final Logger log = LoggerFactory.getLogger(PolicyMatchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CACHE_NAME = "policyMatchCache";

    /** §8 PII 화이트리스트 — company_profile 허용 키 (그 외 제거하여 PII 유입 차단). */
    private static final List<String> PROFILE_WHITELIST = List.of(
            "ksic_code", "employee_count", "growth_stage", "region_code", "annual_revenue");

    private final PolicyMatchingService policyMatchingService;
    private final MlServiceClient mlServiceClient;
    private final PolicyRecommendationLogService logService;
    private final PolicyMatchProperties properties;
    private final CacheManager cacheManager;
    private final PolicyRecommendationLogMapper logMapper;

    public PolicyMatchService(PolicyMatchingService policyMatchingService,
                              MlServiceClient mlServiceClient,
                              PolicyRecommendationLogService logService,
                              PolicyMatchProperties properties,
                              CacheManager cacheManager,
                              PolicyRecommendationLogMapper logMapper) {
        this.policyMatchingService = policyMatchingService;
        this.mlServiceClient = mlServiceClient;
        this.logService = logService;
        this.properties = properties;
        this.cacheManager = cacheManager;
        this.logMapper = logMapper;
    }

    /**
     * 하이브리드 추천: 규칙 후보 + 시맨틱 점수 결합 → hybrid 내림차순 Top-K.
     *
     * <p>회원 컨텍스트면 본문 프로필 대신 SPEC-CMS-007 DB 프로필을 우선 사용한다(REQ-PM-006).
     * ML 실패 시 규칙 단독 폴백 + degraded=true(REQ-PM-009). 추천 이벤트는 비동기 적재(REQ-PM-004).
     */
    // @MX:ANCHOR fan_in≥3: controller·cache·scheduler 호출 지점
    // @MX:REASON 하이브리드 점수 불변식 — wRule+wSemantic=1.0, 정규화 변경 시 AC-PM-006 회귀
    // @MX:SPEC REQ-PM-008
    public PolicyMatchResponse recommend(String rawSessionRef,
                                         PolicyMatchRequest req,
                                         Authentication auth) {
        // 1. Top-K 클램프 [1, topKMax], 미지정 시 기본값 (REQ-PM-002)
        int topK = clampTopK(req.topK());

        // 2. 회원/비회원 식별자 해시 (REQ-PM-014, 평문 미저장)
        String sessionRef = IpHashUtil.sha256Hex(rawSessionRef);

        // 3. 프로필 결정 — 회원이면 DB 프로필 우선 (REQ-PM-006)
        Long companyId = resolveCompanyId(auth);
        Map<String, Object> profile = whitelist(req.companyProfile());

        // 4. 캐시 키 (REQ-PM-003)
        String cacheKey = buildCacheKey(sessionRef, profile, req.queryText(), topK, companyId);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            PolicyMatchResponse cached = cache.get(cacheKey, PolicyMatchResponse.class);
            if (cached != null) {
                return cached;
            }
        }

        // 5. SPEC-CMS-007 규칙 기반 후보 점수 (읽기 전용).
        //    회원 프로필 미존재/비회원이면 규칙 점수 불가 → 활성 정책 풀 기반 시맨틱 단독 후보로 폴백.
        List<MatchedPolicy> ruleMatches;
        if (companyId == null) {
            ruleMatches = activePoolAsZeroRule();
        } else {
            try {
                ruleMatches = policyMatchingService
                        .matchForCompany(companyId, properties.getTopKMax())
                        .results();
            } catch (CompanyMatchInputNotFoundException e) {
                log.warn("회원 매칭 프로필 미존재 — 활성 정책 풀 시맨틱 단독 폴백: companyId={}", companyId);
                ruleMatches = activePoolAsZeroRule();
            }
        }

        // 6. ML 시맨틱 점수 (CircuitBreaker 내부 — REQ-PM-009 폴백)
        boolean degraded;
        Map<Long, MlMatchItem> semanticById = new LinkedHashMap<>();
        try {
            List<Long> candidateIds = ruleMatches.stream().map(MatchedPolicy::policyId).toList();
            MlPolicyMatchResponse mlResp = mlServiceClient.policyMatch(
                    new MlPolicyMatchRequest(profile, req.queryText(), candidateIds, topK));
            if (mlResp != null && mlResp.matches() != null) {
                for (MlMatchItem item : mlResp.matches()) {
                    semanticById.put(item.policyId(), item);
                }
            }
            degraded = false;
        } catch (MlServiceException e) {
            log.warn("policy-match ML 폴백 (규칙 단독 랭킹): {}", e.getMessage());
            degraded = true;
        }

        // 7. 하이브리드 점수 산출 + 설명 구성
        List<PolicyMatchItem> items = new ArrayList<>();
        for (MatchedPolicy rule : ruleMatches) {
            double ruleNorm = rule.score().doubleValue() / 100.0;
            MlMatchItem ml = semanticById.get(rule.policyId());
            double semantic = (!degraded && ml != null) ? ml.semanticScore() : 0.0;
            double hybrid = degraded
                    ? ruleNorm
                    : properties.getWRule() * ruleNorm + properties.getWSemantic() * semantic;

            PolicyMatchExplanation explanation = buildExplanation(rule, ml, degraded);
            items.add(new PolicyMatchItem(rule.policyId(), hybrid, ruleNorm, semantic, explanation));
        }

        // 8. hybrid 내림차순 Top-K (REQ-PM-001)
        items.sort(Comparator.comparingDouble(PolicyMatchItem::hybridScore).reversed());
        List<PolicyMatchItem> top = items.stream().limit(topK).toList();

        PolicyMatchResponse response = new PolicyMatchResponse(top, degraded);

        // 9. 추천 이벤트 비동기 적재 (VIEWED — REQ-PM-004)
        logService.logRecommendation(
                sessionRef, profile, req.queryText(),
                top.stream().map(PolicyMatchItem::policyId).toList(),
                buildMlScores(top, degraded));

        // 10. 캐시 적재 후 반환
        if (cache != null) {
            cache.put(cacheKey, response);
        }
        return response;
    }

    /** Top-K [1, topKMax] 클램프, 미지정 시 기본값 (REQ-PM-002). */
    int clampTopK(Integer requested) {
        if (requested == null) {
            return properties.getTopKDefault();
        }
        if (requested <= 0) {
            return properties.getTopKDefault();
        }
        return Math.min(requested, properties.getTopKMax());
    }

    private Long resolveCompanyId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof kr.co.ircp.cms.domain.auth.security.JwtPrincipal jp) {
            return jp.userId();
        }
        return null;
    }

    /** §8 PII 화이트리스트 필터 — 허용 키만 유지(예기치 않은 PII 키 차단). */
    Map<String, Object> whitelist(Map<String, Object> profile) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (profile == null) {
            return filtered;
        }
        for (String key : PROFILE_WHITELIST) {
            if (profile.containsKey(key) && profile.get(key) != null) {
                filtered.put(key, profile.get(key));
            }
        }
        return filtered;
    }

    private String buildCacheKey(String sessionRef, Map<String, Object> profile,
                                 String queryText, int topK, Long companyId) {
        String profileHash = IpHashUtil.sha256Hex(String.valueOf(profile));
        return String.format("%s|%s|%s|%d|%s",
                sessionRef, profileHash, queryText == null ? "" : queryText, topK,
                companyId == null ? "" : companyId);
    }

    @SuppressWarnings("unchecked")
    private PolicyMatchExplanation buildExplanation(MatchedPolicy rule, MlMatchItem ml,
                                                    boolean degraded) {
        Map<String, Object> ruleBreakdown;
        try {
            ruleBreakdown = rule.scoreBreakdown() == null
                    ? Map.of()
                    : MAPPER.readValue(rule.scoreBreakdown(),
                            new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            ruleBreakdown = Map.of();
        }
        if (degraded || ml == null || ml.explanation() == null) {
            return new PolicyMatchExplanation(ruleBreakdown, List.of(),
                    "규칙 기반 점수만으로 추천됨 (시맨틱 추론 미가용)", false);
        }
        return new PolicyMatchExplanation(
                ruleBreakdown,
                ml.explanation().matchedTerms() == null ? List.of() : ml.explanation().matchedTerms(),
                ml.explanation().rationale(),
                true);
    }

    private Map<String, Object> buildMlScores(List<PolicyMatchItem> items, boolean degraded) {
        Map<String, Object> scores = new LinkedHashMap<>();
        if (degraded) {
            scores.put("_fallback", true);
        }
        for (PolicyMatchItem item : items) {
            if (item == null || item.policyId() == null) {
                continue;
            }
            scores.put(String.valueOf(item.policyId()), Map.of(
                    "semantic", item.semanticScore(),
                    "rule", item.ruleScore(),
                    "hybrid", item.hybridScore()));
        }
        return scores;
    }

    /**
     * 활성 정책 풀을 규칙 점수 0(빈 breakdown)인 후보로 변환.
     * 비회원·회원 프로필 미존재 시 ML 시맨틱 단독 랭킹 후보로 사용한다(에러 대신 200).
     */
    private List<MatchedPolicy> activePoolAsZeroRule() {
        List<Long> ids = logMapper.findActivePolicyIds(properties.getTopKMax());
        List<MatchedPolicy> matches = new ArrayList<>();
        for (Long id : nullSafe(ids)) {
            matches.add(new MatchedPolicy(
                    id, null, null, null,
                    java.math.BigDecimal.ZERO, "D", "{}", java.time.Instant.now()));
        }
        return matches;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** package-private — 단위 테스트에서 하이브리드 산식만 직접 검증(AC-PM-006). */
    double hybrid(double ruleScore0to100, double semanticScore0to1) {
        double ruleNorm = ruleScore0to100 / 100.0;
        return properties.getWRule() * ruleNorm + properties.getWSemantic() * semanticScore0to1;
    }
}
