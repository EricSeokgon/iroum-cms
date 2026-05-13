package kr.co.ircp.cms.domain.content.page.mapper;

import kr.co.ircp.cms.domain.content.page.entity.ContentBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 콘텐츠 블록 MyBatis 매퍼.
 * REQ-CONTENT-006-D: 블록 CRUD + 정렬
 *
 */
@Mapper
public interface ContentBlockMapper {

    /** 페이지의 블록 목록 조회 (sort_order 오름차순) */
    List<ContentBlock> findByPageId(@Param("pageId") Long pageId);

    /** ID로 단건 조회 */
    Optional<ContentBlock> findById(@Param("id") Long id);

    /** 블록 생성 */
    void insert(ContentBlock block);

    /** 블록 수정 */
    int update(ContentBlock block);

    /** 블록 삭제 */
    int deleteById(@Param("id") Long id);

    /** 블록 sort_order 일괄 갱신 */
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);
}
