package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.BlockOrderRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockResponse;

import java.util.List;
import java.util.Set;

/**
 * 콘텐츠 블록 서비스 인터페이스.
 * REQ-CONTENT-006-D: 블록 CRUD + 정렬 + 권한 검증
 *
 * // @MX:ANCHOR: [AUTO] ContentBlockService — 콘텐츠 블록 비즈니스 계약
 * // @MX:REASON: ContentBlockController, PageService에서 fan_in >= 3으로 참조
 * // @MX:SPEC: REQ-CONTENT-006-D
 */
public interface ContentBlockService {

    /** 블록 목록 조회 (sort_order 오름차순) */
    List<ContentBlockResponse> listBlocks(Long pageId);

    /**
     * 블록 생성.
     * RICH_TEXT/MARKDOWN/IMAGE는 서버측 sanitize 적용.
     * HTML 블록은 userAuthorities에 BLOCK:WRITE_HTML 포함 여부로 거부.
     */
    ContentBlockResponse createBlock(Long pageId, ContentBlockRequest request, Set<String> userAuthorities);

    /** 블록 수정 */
    ContentBlockResponse updateBlock(Long pageId, Long blockId, ContentBlockRequest request, Set<String> userAuthorities);

    /** 블록 삭제 */
    void deleteBlock(Long pageId, Long blockId);

    /**
     * 블록 순서 일괄 갱신 (트랜잭션).
     * REQ-CONTENT-006-D-2
     */
    void reorderBlocks(Long pageId, BlockOrderRequest request);
}
