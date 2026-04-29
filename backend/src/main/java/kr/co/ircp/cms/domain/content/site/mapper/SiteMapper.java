package kr.co.ircp.cms.domain.content.site.mapper;

import kr.co.ircp.cms.domain.content.site.entity.Site;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 사이트 MyBatis 매퍼.
 * REQ-CONTENT-003-D: 사이트 마스터 CRUD
 *
 * // @MX:ANCHOR: [AUTO] SiteMapper — 사이트 마스터 데이터 접근 계층
 * // @MX:REASON: SiteService에서 fan_in >= 3으로 참조
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 XML 구현 필요
 */
@Mapper
public interface SiteMapper {

    /** 전체 사이트 목록 조회 */
    List<Site> findAll();

    /** ID로 단건 조회 */
    Optional<Site> findById(@Param("id") Long id);

    /** 도메인으로 단건 조회 */
    Optional<Site> findByDomain(@Param("domain") String domain);

    /** 코드로 단건 조회 */
    Optional<Site> findByCode(@Param("code") String code);

    /** 사이트 수 조회 */
    long count();

    /** 사이트 수정 */
    int update(Site site);
}
