package kr.co.ircp.cms.domain.content.site.service;

import kr.co.ircp.cms.domain.content.site.dto.SiteResponse;
import kr.co.ircp.cms.domain.content.site.dto.SiteUpdateRequest;
import kr.co.ircp.cms.domain.content.site.entity.Site;
import kr.co.ircp.cms.domain.content.site.exception.SiteMultiDisabledException;
import kr.co.ircp.cms.domain.content.site.mapper.SiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 서비스 구현체.
 * REQ-CONTENT-003-D: 사이트 마스터 관리
 *
 * // @MX:ANCHOR: [AUTO] SiteServiceImpl.getCurrentSite — 모든 콘텐츠 요청의 사이트 해석 진입점
 * // @MX:REASON: MenuController, PageController, BannerController 등 fan_in >= 4로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteServiceImpl implements SiteService {

    private final SiteMapper siteMapper;

    /**
     * 도메인으로 현재 사이트 조회.
     * 일치하는 사이트가 없으면 code='MAIN' 기본 사이트로 폴백.
     * REQ-CONTENT-003-D-1, D-2
     *
     * // @MX:NOTE: [AUTO] siteByDomain 캐시 적용 — 모든 콘텐츠 요청 진입점으로 고빈도 조회. TTL 10분.
     */
    @Override
    @Cacheable(value = "siteByDomain", key = "#domain")
    public SiteResponse getCurrentSite(String domain) {
        return siteMapper.findByDomain(domain)
                .or(() -> siteMapper.findByCode("MAIN"))
                .map(SiteResponse::from)
                .orElseThrow(() -> new IllegalStateException("기본 사이트(MAIN)가 존재하지 않습니다."));
    }

    @Override
    @Cacheable(value = "siteByCode", key = "#code")
    public SiteResponse getSiteByCode(String code) {
        return siteMapper.findByCode(code)
                .map(SiteResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. code=" + code));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "siteByDomain", allEntries = true),
            @CacheEvict(value = "siteByCode", allEntries = true)
    })
    public SiteResponse updateSite(Long id, SiteUpdateRequest request) {
        Site site = siteMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. id=" + id));
        site.setName(request.name());
        site.setDomain(request.domain());
        site.setDefaultLanguage(request.defaultLanguage());
        siteMapper.update(site);
        return SiteResponse.from(site);
    }

    /**
     * 멀티사이트 생성.
     * REQ-CONTENT-003-D-3: 1차 출시 기본값 비활성화 → 항상 거부
     */
    @Override
    @Transactional
    public SiteResponse createSite(SiteUpdateRequest request) {
        // REQ-CONTENT-003-D-3: 멀티사이트 비활성화 가드 (1차 출시 고정)
        throw new SiteMultiDisabledException();
    }
}
