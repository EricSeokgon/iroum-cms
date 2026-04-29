package kr.co.ircp.cms.domain.content.site.service;

import kr.co.ircp.cms.domain.content.site.dto.SiteResponse;
import kr.co.ircp.cms.domain.content.site.dto.SiteUpdateRequest;
import kr.co.ircp.cms.domain.content.site.exception.SiteMultiDisabledException;
import kr.co.ircp.cms.domain.content.site.mapper.SiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 서비스 구현체.
 * REQ-CONTENT-003-D: 사이트 마스터 관리
 *
 * // @MX:NOTE: [AUTO] RED 단계 골격. Step 2 GREEN에서 실제 구현.
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 UnsupportedOperationException 본문 제거 후 실제 로직 채움
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteServiceImpl implements SiteService {

    private final SiteMapper siteMapper;

    @Override
    public SiteResponse getCurrentSite(String domain) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public SiteResponse getSiteByCode(String code) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public SiteResponse updateSite(Long id, SiteUpdateRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public SiteResponse createSite(SiteUpdateRequest request) {
        // 멀티사이트 비활성화 가드 (1차 출시 기본값: 비활성화)
        // REQ-CONTENT-003-D-3: 항상 거부
        throw new SiteMultiDisabledException();
    }
}
