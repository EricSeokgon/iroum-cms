package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 페이지 이력 서비스 구현체.
 * REQ-CONTENT-005-D-6/7: 이력 조회, 롤백
 *
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 실제 구현 채움
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageHistoryServiceImpl implements PageHistoryService {

    private final PageHistoryMapper pageHistoryMapper;

    @Override
    public List<PageHistoryResponse> listHistory(Long pageId) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public PageHistoryResponse getHistory(Long pageId, int version) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }
}
