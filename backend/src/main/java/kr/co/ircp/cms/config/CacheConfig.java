package kr.co.ircp.cms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import kr.co.ircp.cms.domain.ai.rag.config.RagProperties;
import kr.co.ircp.cms.domain.policy.aimatch.config.PolicyMatchProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 캐시 설정.
 * REQ-CONTENT-007-D-3: 서비스 레이어 캐시 도입
 * REQ-SYSTEM-004-D: 공통코드 캐시 (codes, codeGroups — TTL 1시간)
 * REQ-SYSTEM-002-D: 대시보드 KPI 캐시 (dashboard — TTL 60초)
 *
 * <p>캐시별 TTL:
 * <ul>
 *   <li>menuTree     — TTL 5분,   max 100 entries</li>
 *   <li>pageBySlug   — TTL 10분,  max 1000 entries</li>
 *   <li>sitemap      — TTL 1시간, max 10 entries</li>
 *   <li>popupActive  — TTL 1분,   max 100 entries</li>
 *   <li>codes        — TTL 1시간, max 500 entries (공통코드)</li>
 *   <li>codeGroups   — TTL 1시간, max 100 entries (공통코드 그룹)</li>
 *   <li>dashboard    — TTL 60초,  max 10 entries  (대시보드 KPI)</li>
 *   <li>siteByDomain — TTL 10분,  max 50 entries  (REQ-CONTENT-007-D-3)</li>
 *   <li>siteByCode   — TTL 10분,  max 50 entries  (REQ-CONTENT-007-D-3)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(PolicyMatchProperties policyMatchProperties,
                                     RagProperties ragProperties) {
        SimpleCacheManager manager = new SimpleCacheManager();

        CaffeineCache menuTree = build("menuTree",
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(100));

        CaffeineCache pageBySlug = build("pageBySlug",
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000));

        CaffeineCache sitemap = build("sitemap",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10));

        CaffeineCache popupActive = build("popupActive",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(100));

        // REQ-SYSTEM-004-D: 공통코드 캐시
        CaffeineCache codes = build("codes",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(500));

        CaffeineCache codeGroups = build("codeGroups",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100));

        // REQ-SYSTEM-002-D: 대시보드 KPI 캐시 (60초 TTL)
        CaffeineCache dashboard = build("dashboard",
                Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(10));

        // SPEC-CMS-AI-001: 성장단계 예측 캐시 (TTL 1시간, max 1000)
        CaffeineCache aiGrowthStage = build("aiGrowthStage",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000));

        // SPEC-CMS-AI-002 REQ-PM-003: 하이브리드 추천 결과 캐시 (TTL PolicyMatchProperties, max 2000)
        CaffeineCache policyMatchCache = build("policyMatchCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(policyMatchProperties.getCacheTtlMinutes(), TimeUnit.MINUTES)
                        .maximumSize(2000));

        // SPEC-CMS-AI-003 REQ-RAG-011/012: RAG 질의 응답 캐시 (질문 해시 키)
        // @MX:NOTE: [AUTO] ragQueryCache — TTL 15분·degraded 응답 캐시 제외 정책 (REQ-RAG-012)
        // @MX:SPEC: SPEC-CMS-AI-003
        CaffeineCache ragQueryCache = build("ragQueryCache",
                Caffeine.newBuilder()
                        .expireAfterWrite(ragProperties.getCacheTtlMinutes(), TimeUnit.MINUTES)
                        .maximumSize(ragProperties.getCacheMaxSize()));

        // SPEC-CMS-AI-004 REQ-AI-TAG-010: 태그 추천 결과 캐시 (본문 SHA-256 해시 키, TTL 30분)
        CaffeineCache tagRecommendationCache = build("tagRecommendationCache",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(2000));

        // REQ-CONTENT-007-D-3: 사이트 조회 캐시 (도메인 매핑 고빈도 — TTL 10분, max 50)
        CaffeineCache siteByDomain = build("siteByDomain",
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(50));

        CaffeineCache siteByCode = build("siteByCode",
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(50));

        manager.setCaches(List.of(menuTree, pageBySlug, sitemap, popupActive,
                codes, codeGroups, dashboard, aiGrowthStage, policyMatchCache, ragQueryCache,
                tagRecommendationCache, siteByDomain, siteByCode));
        return manager;
    }

    private CaffeineCache build(String name, Caffeine<Object, Object> builder) {
        return new CaffeineCache(name, builder.build());
    }
}
