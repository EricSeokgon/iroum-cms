package kr.co.ircp.cms.domain.content.popup.service;

import kr.co.ircp.cms.domain.content.popup.dto.PopupActiveResponse;
import kr.co.ircp.cms.domain.content.popup.dto.PopupRequest;
import kr.co.ircp.cms.domain.content.popup.dto.PopupResponse;

import java.util.List;

/**
 * 팝업 서비스 인터페이스.
 * REQ-CONTENT-008-D: 팝업 CRUD + 활성 팝업 조회
 *
 * // @MX:ANCHOR: [AUTO] PopupService — 팝업 비즈니스 계약
 * // @MX:REASON: PopupController, CacheEvict(popupActive), SitemapService에서 fan_in >= 3으로 참조
 */
public interface PopupService {

    /** 팝업 등록 (기간 검증 + ROLE 타겟 검증 + HTML sanitize) */
    PopupResponse registerPopup(PopupRequest request);

    /** 팝업 수정 */
    PopupResponse updatePopup(Long id, PopupRequest request);

    /** 팝업 삭제 */
    void deletePopup(Long id);

    /** 사이트별 전체 팝업 목록 (관리자용) */
    List<PopupResponse> getPopupsBySite(Long siteId);

    /**
     * 활성 팝업 목록 조회 (공개 API).
     * REQ-CONTENT-008-D-2: 노출 시간 윈도우 + 상위 5개 우선 + display_priority DESC
     */
    List<PopupActiveResponse> getActivePopups(Long siteId);
}
