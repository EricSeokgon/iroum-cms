package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.FaqReorderItem;
import kr.co.ircp.cms.domain.board.entity.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FAQ MyBatis 매퍼.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색
 */
@Mapper
public interface FaqMapper {

    /** FAQ 목록 조회 (카테고리별, 정렬 순) */
    List<Faq> findByCategoryCode(@Param("categoryCode") String categoryCode);

    /** 전체 FAQ 목록 조회 */
    List<Faq> findAll();

    /** ID로 단건 조회 */
    Optional<Faq> findById(@Param("id") Long id);

    /** FAQ 삽입 */
    void insert(Faq faq);

    /** FAQ 수정 */
    int update(Faq faq);

    /** FAQ 삭제 (소프트 삭제) */
    int deleteById(@Param("id") Long id);

    /**
     * 필터 기반 페이징 조회.
     * 카테고리·키워드(질문/답변 ILIKE)·정렬 순서 적용.
     */
    List<Faq> findWithFilters(
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /** 필터 기반 카운트 (페이징 totalElements 계산용) */
    long countWithFilters(
            @Param("category") String category,
            @Param("keyword") String keyword
    );

    /** 카테고리별 개수 GROUP BY 조회 */
    List<Map<String, Object>> countByCategory();

    /** 정렬 순서 일괄 업데이트 */
    void batchUpdateSortOrder(@Param("items") List<FaqReorderItem> items);

    /** 조회수 1 증가 */
    void incrementViewCount(@Param("id") Long id);
}
