package kr.co.ircp.cms.domain.content.seo.service;

import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectRequest;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectResponse;

import java.util.List;
import java.util.Optional;

/**
 * SEO 리다이렉트 서비스 인터페이스.
 * REQ-CONTENT-005-D-8: URL 리다이렉트 관리
 *
 * // @MX:ANCHOR: [AUTO] SeoRedirectService — SEO 리다이렉트 비즈니스 계약
 * // @MX:REASON: SeoRedirectController, PageServiceImpl에서 fan_in >= 3으로 참조
 */
public interface SeoRedirectService {

    /** 리다이렉트 목록 조회 */
    List<SeoRedirectResponse> getAllRedirects();

    /** 활성 리다이렉트 단건 조회 (라우팅용) */
    Optional<SeoRedirectResponse> getActiveRedirectByFromPath(String fromPath);

    /** 리다이렉트 생성 (301 기본값) */
    SeoRedirectResponse createRedirect(SeoRedirectRequest request);

    /** slug 변경 시 자동 upsert (PageServiceImpl에서 호출) */
    void upsertFromSlugChange(String oldPath, String newPath, String reason);

    /** 리다이렉트 비활성화 */
    void deactivateRedirect(Long id);

    /** 리다이렉트 삭제 */
    void deleteRedirect(Long id);
}
