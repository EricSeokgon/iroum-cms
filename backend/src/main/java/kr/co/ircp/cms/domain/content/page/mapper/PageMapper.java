package kr.co.ircp.cms.domain.content.page.mapper;

import kr.co.ircp.cms.domain.content.page.entity.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 페이지 MyBatis 매퍼.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회
 *
 * // @MX:ANCHOR: [AUTO] PageMapper — 페이지 핵심 데이터 접근 계층
 * // @MX:REASON: PageService에서 fan_in >= 3으로 참조 (CRUD + 발행 + 이력)
 */
@Mapper
public interface PageMapper {

    /** ID로 단건 조회 */
    Optional<Page> findById(@Param("id") Long id);

    /** slug로 단건 조회 (시민 라우팅) */
    Optional<Page> findBySiteIdAndSlug(@Param("siteId") Long siteId, @Param("slug") String slug);

    /** slug 유일성 확인 */
    boolean existsBySiteIdAndSlug(@Param("siteId") Long siteId, @Param("slug") String slug);

    /** 코드 유일성 확인 */
    boolean existsBySiteIdAndCode(@Param("siteId") Long siteId, @Param("code") String code);

    /** 페이지 생성 */
    void insert(Page page);

    /** 페이지 수정 (낙관적 잠금 없음 — 롤백 등 내부 경로 전용) */
    int update(Page page);

    /**
     * 페이지 수정 (낙관적 잠금).
     * SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005: WHERE current_version = #{currentVersion}
     * (서비스가 expectedVersion 을 주입) + current_version + 1. 반환 0 → 버전 불일치(409).
     * 기존 rollback 경로(update)의 동작을 보존하기 위해 별도 메서드로 분리한다.
     */
    int updateWithVersion(Page page);

    /** 상태 갱신 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 발행 처리 (status=PUBLISHED, published_at=now, scheduled_at=NULL) */
    int publish(@Param("id") Long id);

    /** 예약 처리 (status=SCHEDULED, scheduled_at=?)) */
    int schedule(@Param("id") Long id, @Param("scheduledAt") java.time.Instant scheduledAt);

    /** 철회 처리 (status=RETRACTED) */
    int retract(@Param("id") Long id);

    /** 예약 발행 배치 대상 목록 */
    List<Page> findScheduledDue();

    /** 사이트별 PUBLISHED 페이지 목록 (sitemap 생성용) */
    List<Page> findPublishedBySiteId(@Param("siteId") Long siteId);

    /** seo_redirect 자동 INSERT (slug 변경 시) */
    void insertSeoRedirect(
            @Param("fromPath") String fromPath,
            @Param("toPath") String toPath,
            @Param("reason") String reason
    );

    /** 관리자용 페이지 목록 조회 (사이트/상태/검색 필터 + 페이징) */
    List<Page> listBySiteId(
            @Param("siteId") Long siteId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /** 관리자용 페이지 총 수 조회 */
    long countBySiteId(
            @Param("siteId") Long siteId,
            @Param("status") String status,
            @Param("search") String search
    );
}
