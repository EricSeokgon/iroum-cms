package kr.co.ircp.cms.domain.content.site.service;

import kr.co.ircp.cms.domain.content.site.dto.SiteResponse;
import kr.co.ircp.cms.domain.content.site.dto.SiteUpdateRequest;

/**
 * 사이트 서비스 인터페이스.
 * REQ-CONTENT-003-D: 사이트 마스터 관리
 *
 * // @MX:ANCHOR: [AUTO] SiteService — 사이트 비즈니스 계약
 * // @MX:REASON: SiteController, MenuService, PageService에서 참조 (fan_in >= 3)
 * // @MX:SPEC: REQ-CONTENT-003-D
 */
public interface SiteService {

    /**
     * 현재 사이트 조회 (도메인 매칭).
     * 호스트 헤더의 domain과 일치하는 site row를 반환.
     * 미일치 시 default site(MAIN) 반환.
     */
    SiteResponse getCurrentSite(String domain);

    /**
     * 코드로 사이트 조회.
     */
    SiteResponse getSiteByCode(String code);

    /**
     * 사이트 정보 수정.
     */
    SiteResponse updateSite(Long id, SiteUpdateRequest request);

    /**
     * 신규 사이트 생성 시도.
     * 멀티사이트 비활성화 상태에서는 SiteMultiDisabledException 발생.
     * REQ-CONTENT-003-D-3
     */
    SiteResponse createSite(SiteUpdateRequest request);
}
