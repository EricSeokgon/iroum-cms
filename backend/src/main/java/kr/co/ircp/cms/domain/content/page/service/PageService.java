package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.PageCreateRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.dto.PagePublishRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageScheduleRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;

import java.util.List;

/**
 * 페이지 서비스 인터페이스.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 이력
 *
 * // @MX:ANCHOR: [AUTO] PageService — 페이지 비즈니스 계약
 * // @MX:REASON: PageController, ContentBlockController, PageHistoryService에서 fan_in >= 3으로 참조
 * // @MX:SPEC: REQ-CONTENT-005-D
 */
public interface PageService {

    /** 페이지 생성 (slug 패턴·유일성 검증) */
    PageResponse createPage(PageCreateRequest request, Long createdBy);

    /** 페이지 수정 (이력 누적 + slug 변경 시 seo_redirect 자동 INSERT) */
    PageResponse updatePage(Long id, PageUpdateRequest request, Long updatedBy);

    /** 즉시 발행 */
    PageResponse publishPage(Long id, PagePublishRequest request, Long publishedBy);

    /** 예약 발행 (scheduledAt > now 검증) */
    PageResponse schedulePage(Long id, PageScheduleRequest request, Long scheduledBy);

    /** 철회 */
    PageResponse retractPage(Long id, Long retractedBy);

    /** 이력 목록 조회 (version DESC) */
    List<PageHistoryResponse> getPageHistory(Long id);

    /** 특정 버전으로 롤백 (status=DRAFT 강제) */
    PageResponse rollbackPage(Long id, int version, Long rolledBackBy);

    /** slug로 발행된 페이지 조회 (시민 라우팅 — DRAFT/SCHEDULED/RETRACTED는 404) */
    PageResponse getPublishedPageBySlug(Long siteId, String slug);
}
