package kr.co.ircp.cms.domain.content.banner.mapper;

import kr.co.ircp.cms.domain.content.banner.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 배너 MyBatis 매퍼.
 * REQ-CONTENT-009-D: 배너 CRUD + 클릭 카운트 + 활성 배너 조회
 *
 * // @MX:ANCHOR: [AUTO] BannerMapper — 배너 데이터 접근 계층
 * // @MX:REASON: BannerService, BannerController에서 fan_in >= 3으로 참조
 */
@Mapper
public interface BannerMapper {

    Optional<Banner> findById(@Param("id") Long id);

    /**
     * 그룹 + 시간 윈도우로 활성 배너 조회.
     * REQ-CONTENT-009-D-2: sort_order ASC
     */
    List<Banner> findActiveByGroupAndTimeWindow(
            @Param("bannerGroupCode") String bannerGroupCode,
            @Param("now") Instant now
    );

    void insert(Banner banner);

    int update(Banner banner);

    int deleteById(@Param("id") Long id);

    /**
     * 클릭 카운트 원자적 증가.
     * REQ-CONTENT-009-D-3: UPDATE banner SET click_count = click_count + 1
     */
    int incrementClickCount(@Param("id") Long id);

    /** 관리자용 배너 목록 조회 (사이트/그룹 필터) */
    List<Banner> findAdminBySiteId(
            @Param("siteId") Long siteId,
            @Param("bannerGroupCode") String bannerGroupCode
    );

    /** 사이트별 배너 그룹 코드 목록 조회 (중복 제거) */
    List<String> findGroupsBySiteId(@Param("siteId") Long siteId);
}
