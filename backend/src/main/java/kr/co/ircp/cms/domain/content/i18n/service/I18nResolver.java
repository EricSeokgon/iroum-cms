package kr.co.ircp.cms.domain.content.i18n.service;

import kr.co.ircp.cms.domain.content.i18n.dto.I18nResourceItem;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nResponse;

import java.util.List;

/**
 * 다국어 리소스 리졸버 인터페이스.
 * REQ-CONTENT-010-D-2: 언어 폴백 체인 (요청 언어 → site.default_language → 'ko')
 *
 * // @MX:ANCHOR: [AUTO] I18nResolver — 다국어 폴백 계약
 * // @MX:REASON: I18nController, PopupService, BannerService에서 fan_in >= 3으로 참조
 */
public interface I18nResolver {

    /**
     * 폴백 체인으로 다국어 필드 해석.
     * 1. 요청 언어 조회
     * 2. 없는 필드는 site.default_language로 폴백
     * 3. 그래도 없으면 'ko' 폴백
     */
    I18nResponse resolveFields(String namespace, Long resourceId, String language);

    /**
     * 다국어 리소스 배치 upsert.
     * REQ-CONTENT-010-D: UNIQUE ON CONFLICT UPDATE
     */
    void bulkUpsert(List<I18nResourceItem> items);
}
