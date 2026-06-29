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
     * 페이지 이력 전체 건수.
     * SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006) — 리비전 보존 정책 카운트.
     */
    long countByPageId(@Param("pageId") Long pageId);

    /**
     * 최신 {@code keepCount}개를 제외한 오래된 이력 삭제.
     * SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006) — 리비전 보존 정책 적용.
     * version DESC 기준 상위 keepCount 행만 남기고 나머지를 삭제한다.
     *
     * @param pageId    페이지 ID
     * @param keepCount 보존할 최신 이력 개수
     * @return 삭제된 행 수
     */
    int deleteOldestByPageId(@Param("pageId") Long pageId, @Param("keepCount") int keepCount);
}
