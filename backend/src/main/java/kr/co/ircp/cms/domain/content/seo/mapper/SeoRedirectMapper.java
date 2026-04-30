package kr.co.ircp.cms.domain.content.seo.mapper;

import kr.co.ircp.cms.domain.content.seo.entity.SeoRedirect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * SEO 리다이렉트 MyBatis 매퍼.
 * REQ-CONTENT-005-D-8: URL 리다이렉트 관리
 *
 * // @MX:ANCHOR: [AUTO] SeoRedirectMapper — SEO 리다이렉트 데이터 접근 계층
 * // @MX:REASON: SeoRedirectService, PageServiceImpl에서 fan_in >= 3으로 참조
 */
@Mapper
public interface SeoRedirectMapper {

    List<SeoRedirect> findAll();

    Optional<SeoRedirect> findActiveByFromPath(@Param("fromPath") String fromPath);

    /** slug 변경 시 자동 INSERT (ON CONFLICT UPDATE) */
    void upsert(SeoRedirect redirect);

    int deleteById(@Param("id") Long id);

    int deactivate(@Param("id") Long id);
}
