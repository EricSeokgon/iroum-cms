package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.PublicationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 발간자료 카테고리 MyBatis 매퍼.
 * REQ-BOARD-012-D: 카테고리 트리 조회
 */
@Mapper
public interface PublicationCategoryMapper {

    /** 활성 카테고리 전체 조회 (sort_order, id 정렬). */
    List<PublicationCategory> findAllActive();

    /** ID로 단건 조회. */
    Optional<PublicationCategory> findById(@Param("id") Long id);
}
