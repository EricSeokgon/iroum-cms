package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.search.dto.SynonymCreateRequest;
import kr.co.ircp.cms.domain.search.dto.SynonymUpdateRequest;
import kr.co.ircp.cms.domain.search.entity.SearchSynonym;

/**
 * 동의어 사전 서비스 인터페이스.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: 동의어 CRUD + 쿼리 확장.
 */
public interface SynonymService {

    /** 활성 동의어 목록 (페이징) */
    PageResponse<SearchSynonym> listSynonyms(String locale, int page, int size);

    /** 동의어 등록 — term==synonym 시 SynonymSelfException, 중복 시 DuplicateSynonymException */
    SearchSynonym createSynonym(SynonymCreateRequest req, Long createdBy);

    /** 동의어 수정 — id 미존재 시 SynonymNotFoundException */
    SearchSynonym updateSynonym(Long id, SynonymUpdateRequest req, Long updatedBy);

    /** 동의어 soft delete (status=PAUSED) */
    void deleteSynonym(Long id, Long updatedBy);

    /**
     * 검색 쿼리 동의어 OR 확장.
     *
     * <p>입력 query를 공백 토큰화 후 각 토큰에 대해 활성 동의어를 OR로 확장.
     * 최종 토큰 수를 20개 이내로 절단(RISK-S-05).
     *
     * @return websearch_to_tsquery 친화 형태의 확장 쿼리 (예: "수도 OR 서울"). 입력이 비어있거나 확장 결과가 없으면 원본 반환.
     */
    String expandQuery(String query, String locale);
}
