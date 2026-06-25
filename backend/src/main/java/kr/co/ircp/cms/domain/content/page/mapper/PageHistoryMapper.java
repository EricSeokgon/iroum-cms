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

    /**
     * 페이지 이력 건수 조회.
     * REQ-PHIST-001: 보존 한도 초과 판단용.
     */
    int countByPageId(@Param("pageId") Long pageId);

    /**
     * 페이지별 최신 keepCount개 version을 보존하고 나머지 오래된 이력을 삭제한다.
     * REQ-PHIST-001: 보존 정책 정리. 항상 최신 version부터 보존하므로 currentVersion은 삭제되지 않는다.
     *
     * @param pageId    대상 페이지 ID
     * @param keepCount 보존할 최신 version 개수
     */
    void deleteOldestByPageId(@Param("pageId") Long pageId, @Param("keepCount") int keepCount);
}
