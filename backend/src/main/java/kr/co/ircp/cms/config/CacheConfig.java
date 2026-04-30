package kr.co.ircp.cms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
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
 *
 * <p>캐시별 TTL:
 * <ul>
 *   <li>menuTree  — TTL 5분,  max 100 entries</li>
 *   <li>pageBySlug — TTL 10분, max 1000 entries</li>
 *   <li>sitemap    — TTL 1시간, max 10 entries</li>
 *   <li>popupActive — TTL 1분,  max 100 entries</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();

        CaffeineCache menuTree = build("menuTree",
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(100));

        CaffeineCache pageBySlug = build("pageBySlug",
                Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000));

        CaffeineCache sitemap = build("sitemap",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10));

        CaffeineCache popupActive = build("popupActive",
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(100));

        manager.setCaches(List.of(menuTree, pageBySlug, sitemap, popupActive));
        return manager;
    }

    private CaffeineCache build(String name, Caffeine<Object, Object> builder) {
        return new CaffeineCache(name, builder.build());
    }
}
