package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.search.dto.AutocompleteItem;
import kr.co.ircp.cms.domain.search.dto.PopularQueryItem;
import kr.co.ircp.cms.domain.search.dto.SearchRequest;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.dto.SearchStatsResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 통합 검색 서비스 인터페이스.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001/002/004/005/006/008.
 */
public interface SearchService {

    /** 통합 검색 + 동의어 확장 + facets + 비공개 가드 + 비동기 로그. */
    SearchResponse search(SearchRequest req, Long requesterId, boolean isAdmin, String sessionId, String ipHash);

    /** 자동완성 (인기검색어 + 콘텐츠 제목 통합 정렬, similarity ≥ 0.3). */
    List<AutocompleteItem> autocomplete(String prefix, String locale, int limit);

    /** 인기 검색어 TOP-N (DAILY/WEEKLY/MONTHLY). */
    List<PopularQueryItem> getPopular(String periodType, String locale, int limit);

    /** 검색 결과 클릭 추적 (30분 윈도우 + session/user 매칭). */
    void recordClick(Long searchLogId, String docType, Long docId, Integer rank,
                     Long requesterId, String sessionId);

    /**
     * 운영자용 검색 통계 (REQ-SEARCH-008 §6.6).
     *
     * @param from   집계 시작일 (NULL이면 7일 전)
     * @param to     집계 종료일 (NULL이면 오늘)
     * @param limit  topQueries 결과 수 (기본 20)
     */
    SearchStatsResponse getStats(LocalDate from, LocalDate to, int limit);
}
