package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
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
}
