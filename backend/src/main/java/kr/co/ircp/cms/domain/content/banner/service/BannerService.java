package kr.co.ircp.cms.domain.content.banner.service;

import kr.co.ircp.cms.domain.content.banner.dto.BannerRequest;
import kr.co.ircp.cms.domain.content.banner.dto.BannerResponse;

import java.util.List;

/**
 * 배너 서비스 인터페이스.
 * REQ-CONTENT-009-D: 배너 CRUD + 클릭 카운트 + 활성 배너 조회
 *
 * // @MX:ANCHOR: [AUTO] BannerService — 배너 비즈니스 계약
 * // @MX:REASON: BannerController에서 fan_in >= 3으로 참조
 */
public interface BannerService {

    /** 배너 등록 (기간 검증 + alt_text 검증) */
    BannerResponse registerBanner(BannerRequest request);

    /** 배너 수정 */
    BannerResponse updateBanner(Long id, BannerRequest request);

    /** 배너 삭제 */
    void deleteBanner(Long id);

    /**
     * 그룹별 활성 배너 조회 (PUBLIC).
     * REQ-CONTENT-009-D-2: sort_order ASC
     */
    List<BannerResponse> getActiveBannersByGroup(String bannerGroupCode);

    /**
     * 클릭 이벤트 기록.
     * REQ-CONTENT-009-D-3: click_count++ 원자적 UPDATE + audit_log 기록
     */
    void recordClick(Long id);

    /** 관리자용 배너 목록 조회 (사이트/그룹 필터) */
    List<BannerResponse> listBanners(Long siteId, String groupCode);

    /** 사이트별 배너 그룹 코드 목록 조회 */
    List<String> listGroups(Long siteId);
}
