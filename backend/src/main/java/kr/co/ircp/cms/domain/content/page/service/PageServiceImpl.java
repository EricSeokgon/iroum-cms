package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.PageCreateRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.dto.PagePublishRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageScheduleRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.mapper.ContentBlockMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 페이지 서비스 구현체.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 이력
 *
 * // @MX:NOTE: [AUTO] RED 단계 골격. Step 2 GREEN에서 실제 구현.
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 UnsupportedOperationException 제거 후 실제 로직 채움
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageServiceImpl implements PageService {

    private final PageMapper pageMapper;
    private final ContentBlockMapper contentBlockMapper;
    private final PageHistoryMapper pageHistoryMapper;

    @Override
    @Transactional
    public PageResponse createPage(PageCreateRequest request, Long createdBy) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public PageResponse updatePage(Long id, PageUpdateRequest request, Long updatedBy) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public PageResponse publishPage(Long id, PagePublishRequest request, Long publishedBy) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public PageResponse schedulePage(Long id, PageScheduleRequest request, Long scheduledBy) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public PageResponse retractPage(Long id, Long retractedBy) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public List<PageHistoryResponse> getPageHistory(Long id) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public PageResponse rollbackPage(Long id, int version, Long rolledBackBy) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public PageResponse getPublishedPageBySlug(Long siteId, String slug) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }
}
