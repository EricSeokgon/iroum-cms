package kr.co.ircp.cms.domain.search.repository;

import kr.co.ircp.cms.domain.search.entity.SearchSynonym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 동의어 사전 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: 동의어 CRUD + 활성 동의어 조회 (검색 시 OR 확장용).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 동의어 사전 매퍼 (REQ-SEARCH-009)
@Mapper
public interface SearchSynonymMapper {

    /**
     * 활성(ACTIVE) 동의어 조회 (term + locale 기준).
     * 검색 시 ts_query OR 확장에 사용.
     */
    List<SearchSynonym> findActiveByTerm(
            @Param("term") String term,
            @Param("locale") String locale
    );

    /** 운영자 CRUD 용 페이징 조회 (locale 필터, ACTIVE 한정) */
    List<SearchSynonym> findAllActive(
            @Param("locale") String locale,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /** 페이징 totalElements 계산용 */
    long countAllActive(@Param("locale") String locale);

    /** ID로 단건 조회 */
    Optional<SearchSynonym> findById(@Param("id") Long id);

    /** 동의어 등록. UNIQUE 위반은 SQLException 자연 전파 (서비스 레이어에서 409 매핑) */
    void insert(SearchSynonym synonym);

    /** 부분 갱신: synonym 본문, status, updated_by/at */
    int update(
            @Param("id") Long id,
            @Param("synonym") String synonym,
            @Param("status") String status,
            @Param("updatedBy") Long updatedBy
    );

    /** soft delete (status=PAUSED) */
    int softDelete(
            @Param("id") Long id,
            @Param("updatedBy") Long updatedBy
    );

    /** 사전 중복 검사 (등록 전 409 응답 결정용) */
    boolean existsByTermAndSynonym(
            @Param("term") String term,
            @Param("synonym") String synonym,
            @Param("locale") String locale
    );
}
