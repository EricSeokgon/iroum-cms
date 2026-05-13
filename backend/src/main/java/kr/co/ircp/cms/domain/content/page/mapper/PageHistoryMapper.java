package kr.co.ircp.cms.domain.content.page.mapper;

import kr.co.ircp.cms.domain.content.page.entity.PageHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 페이지 변경 이력 MyBatis 매퍼.
 * REQ-CONTENT-005-D-2/6/7: 이력 누적, 조회, 롤백
 *
 */
@Mapper
public interface PageHistoryMapper {

    /** 이력 목록 조회 (version DESC) */
    List<PageHistory> findByPageId(@Param("pageId") Long pageId);

    /** 특정 버전 이력 조회 */
    Optional<PageHistory> findByPageIdAndVersion(@Param("pageId") Long pageId, @Param("version") int version);

    /** 이력 INSERT */
    void insert(PageHistory history);
}
