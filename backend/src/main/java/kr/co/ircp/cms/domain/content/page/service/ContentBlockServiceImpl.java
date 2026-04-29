package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.BlockOrderRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockResponse;
import kr.co.ircp.cms.domain.content.page.mapper.ContentBlockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 콘텐츠 블록 서비스 구현체.
 * REQ-CONTENT-006-D: 블록 CRUD + 정렬 + sanitize + 권한 검증
 *
 * // @MX:NOTE: [AUTO] RED 단계 골격. Step 2 GREEN에서 실제 구현.
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 UnsupportedOperationException 제거 후 실제 로직 채움
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentBlockServiceImpl implements ContentBlockService {

    private final ContentBlockMapper contentBlockMapper;

    @Override
    public List<ContentBlockResponse> listBlocks(Long pageId) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public ContentBlockResponse createBlock(Long pageId, ContentBlockRequest request, Set<String> userAuthorities) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public ContentBlockResponse updateBlock(Long pageId, Long blockId, ContentBlockRequest request, Set<String> userAuthorities) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public void deleteBlock(Long pageId, Long blockId) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public void reorderBlocks(Long pageId, BlockOrderRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }
}
