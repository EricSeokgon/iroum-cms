package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.FaqCategoryCount;
import kr.co.ircp.cms.domain.board.dto.FaqCreateRequest;
import kr.co.ircp.cms.domain.board.dto.FaqDetail;
import kr.co.ircp.cms.domain.board.dto.FaqReorderRequest;
import kr.co.ircp.cms.domain.board.dto.FaqSummary;
import kr.co.ircp.cms.domain.board.dto.FaqUpdateRequest;

import java.util.List;

/**
 * FAQ 서비스 인터페이스.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색
 */
public interface FaqService {

    /** FAQ 목록 페이징 조회 (카테고리·키워드 필터). */
    PageResponse<FaqSummary> listFaqs(String category, String keyword, int page, int size);

    /** FAQ 단건 상세 조회 (조회수 증가). */
    FaqDetail getFaq(Long id);

    /** 카테고리별 FAQ 개수 조회. */
    List<FaqCategoryCount> getCategories();

    /** FAQ 생성 (관리자). */
    FaqDetail createFaq(FaqCreateRequest request, Long createdBy);

    /** FAQ 수정 (관리자). */
    FaqDetail updateFaq(Long id, FaqUpdateRequest request);

    /** FAQ 삭제 (관리자). */
    void deleteFaq(Long id);

    /** FAQ 정렬 순서 일괄 변경 (관리자). */
    void reorderFaqs(FaqReorderRequest request);
}
