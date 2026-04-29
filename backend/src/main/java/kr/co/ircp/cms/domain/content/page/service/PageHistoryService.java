package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;

import java.util.List;

/**
 * 페이지 이력 서비스 인터페이스.
 * REQ-CONTENT-005-D-6/7: 이력 조회, 롤백
 */
public interface PageHistoryService {

    /** 이력 목록 조회 (version DESC) */
    List<PageHistoryResponse> listHistory(Long pageId);

    /** 특정 버전 이력 조회 */
    PageHistoryResponse getHistory(Long pageId, int version);
}
