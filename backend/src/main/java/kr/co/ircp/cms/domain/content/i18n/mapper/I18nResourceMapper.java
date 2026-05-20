package kr.co.ircp.cms.domain.content.i18n.mapper;

import kr.co.ircp.cms.domain.content.i18n.entity.I18nResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 다국어 리소스 MyBatis 매퍼.
 * REQ-CONTENT-010-D: 다국어 리소스 조회 + bulk upsert
 *
 * // @MX:ANCHOR: [AUTO] I18nResourceMapper — 다국어 리소스 데이터 접근 계층
 * // @MX:REASON: I18nResolver, I18nController에서 fan_in >= 3으로 참조
 */
@Mapper
public interface I18nResourceMapper {

    /**
     * namespace + resourceId + language로 리소스 목록 조회.
     */
    List<I18nResource> findByNamespaceAndResourceIdAndLanguage(
            @Param("namespace") String namespace,
            @Param("resourceId") Long resourceId,
            @Param("language") String language
    );

    /**
     * namespace 전체 목록 조회 (페이지네이션).
     * REQ-CONTENT-010-D-3: 다국어 리소스 목록 편집기용
     */
    List<I18nResource> findByNamespace(
            @Param("namespace") String namespace,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * namespace 전체 건수 조회.
     */
    int countByNamespace(@Param("namespace") String namespace);

    /**
     * 배치 upsert: UNIQUE (namespace, resource_id, language, field_name) ON CONFLICT UPDATE.
     * REQ-CONTENT-010-D
     */
    void upsertBatch(@Param("items") List<I18nResource> items);
}
