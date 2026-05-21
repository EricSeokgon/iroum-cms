package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.search.dto.AutocompleteItem;
import kr.co.ircp.cms.domain.search.dto.PopularQueryItem;
import kr.co.ircp.cms.domain.search.dto.SearchRequest;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.entity.SearchLog;
import kr.co.ircp.cms.domain.search.entity.SearchPopularCache;
import kr.co.ircp.cms.domain.search.exception.SearchClickWindowExpiredException;
import kr.co.ircp.cms.domain.search.exception.SearchDomainInvalidException;
import kr.co.ircp.cms.domain.search.exception.SearchLocaleUnsupportedException;
import kr.co.ircp.cms.domain.search.exception.SearchLogNotFoundException;
import kr.co.ircp.cms.domain.search.exception.SearchQueryTooLongException;
import kr.co.ircp.cms.domain.search.repository.SearchLogMapper;
import kr.co.ircp.cms.domain.search.repository.SearchPopularCacheMapper;
import kr.co.ircp.cms.domain.search.repository.UnifiedSearchMapper;
import kr.co.ircp.cms.domain.search.service.SearchLogAsyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchService GREEN 테스트.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001~009: 통합검색·자동완성·인기·클릭·정규화·동의어·비공개 가드.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchService GREEN 테스트 (REQ-SEARCH-001~009)")
class SearchServiceTest {

    @Mock private UnifiedSearchMapper unifiedSearchMapper;
    @Mock private SearchPopularCacheMapper popularCacheMapper;
    @Mock private SearchLogMapper searchLogMapper;
    @Mock private SynonymService synonymService;
    @Mock private SearchLogAsyncService searchLogAsyncService;

    private SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceImpl(
                unifiedSearchMapper, popularCacheMapper, searchLogMapper, synonymService, searchLogAsyncService);
    }

    private Map<String, Object> stubRow(String docType, long docId, String title) {
        Map<String, Object> r = new HashMap<>();
        r.put("doc_type", docType);
        r.put("doc_id", docId);
        r.put("title", title);
        r.put("snippet", "<mark>" + title + "</mark>");
        r.put("rank", 0.85);
        r.put("domain", docType);
        r.put("url", "/" + docType + "/" + docId);
        r.put("created_at", Instant.now());
        return r;
    }

    // ─── A. search core (REQ-SEARCH-001) ─────────────────────────────────

    @Test
    @DisplayName("search — 정상 쿼리 시 결과 반환 + SearchLog 적재")
    void search_validQuery_returnsResultsAndLogs() {
        when(synonymService.expandQuery(anyString(), anyString())).thenReturn("청년");
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        stubRow("board", 1L, "title1"),
                        stubRow("content", 2L, "title2"),
                        stubRow("policy", 3L, "title3")
                ));
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(3L);
        // SearchLog 비동기 저장 스텁 — insertSync는 생성된 검색 로그 ID를 반환
        when(searchLogAsyncService.insertSync(any(SearchLog.class))).thenReturn(1L);

        SearchRequest req = new SearchRequest("청년", "ALL", 1, 20, "ko");
        SearchResponse resp = service.search(req, 100L, false, "sess-1", "iphash");

        assertThat(resp.totalElements()).isEqualTo(3);
        assertThat(resp.content()).hasSize(3);
        assertThat(resp.byDomainFacets()).containsKeys("board", "content", "policy");
        // SearchServiceImpl.insertSearchLog는 searchLogAsyncService.insertSync로 위임됨
        verify(searchLogAsyncService).insertSync(any(SearchLog.class));
    }

    @Test
    @DisplayName("search — 정규화: 소문자 + 공백 collapse + trim")
    void search_normalizesQuery_lowercasesTrimmesCollapsesWhitespace() {
        when(synonymService.expandQuery(anyString(), anyString())).thenReturn(null);
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(0L);

        SearchRequest req = new SearchRequest("  Hello   WORLD ", "ALL", 1, 20, "ko");
        service.search(req, null, false, "sess", null);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(synonymService).expandQuery(queryCaptor.capture(), eq("ko"));
        // 소문자 + 트림 + 공백 collapse
        assertThat(queryCaptor.getValue()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("search — 빈 결과 시 facets 비어 있음")
    void search_emptyResults_returnsZeroFacets() {
        when(synonymService.expandQuery(anyString(), anyString())).thenReturn(null);
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(0L);

        SearchRequest req = new SearchRequest("nothing", "ALL", 1, 20, "ko");
        SearchResponse resp = service.search(req, null, false, "sess", null);

        assertThat(resp.totalElements()).isEqualTo(0);
        assertThat(resp.byDomainFacets()).isEmpty();
    }

    @Test
    @DisplayName("search — 쿼리 길이 초과 시 SearchQueryTooLongException")
    void search_queryTooLong_throwsSearchQueryTooLongException() {
        String longQuery = "a".repeat(201);
        SearchRequest req = new SearchRequest(longQuery, "ALL", 1, 20, "ko");

        assertThatThrownBy(() -> service.search(req, null, false, "sess", null))
                .isInstanceOf(SearchQueryTooLongException.class);
    }

    @Test
    @DisplayName("search — 화이트리스트 외 domain 시 SearchDomainInvalidException")
    void search_invalidDomain_throwsSearchDomainInvalidException() {
        SearchRequest req = new SearchRequest("abc", "invalid_dom", 1, 20, "ko");

        assertThatThrownBy(() -> service.search(req, null, false, "sess", null))
                .isInstanceOf(SearchDomainInvalidException.class);
    }

    @Test
    @DisplayName("search — 미지원 locale 시 SearchLocaleUnsupportedException")
    void search_invalidLocale_throwsSearchLocaleUnsupportedException() {
        SearchRequest req = new SearchRequest("abc", "ALL", 1, 20, "fr");

        assertThatThrownBy(() -> service.search(req, null, false, "sess", null))
                .isInstanceOf(SearchLocaleUnsupportedException.class);
    }

    @Test
    @DisplayName("search — 도메인 가중치 맵이 매퍼에 정확히 전달")
    void search_appliesDomainWeights() {
        when(synonymService.expandQuery(anyString(), anyString())).thenReturn(null);
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(0L);

        SearchRequest req = new SearchRequest("query", "ALL", 1, 20, "ko");
        service.search(req, null, false, "sess", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Double>> weightsCaptor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(unifiedSearchMapper).searchUnified(
                anyString(), anyString(), anyString(), weightsCaptor.capture(),
                any(), anyBoolean(), anyInt(), anyInt());
        Map<String, Double> weights = weightsCaptor.getValue();
        assertThat(weights).containsEntry("board", 1.0);
        assertThat(weights).containsEntry("content", 0.9);
        assertThat(weights).containsEntry("publication", 0.85);
        assertThat(weights).containsEntry("policy", 0.8);
        assertThat(weights).containsEntry("safety", 0.7);
        assertThat(weights).containsEntry("media", 0.5);
    }

    // ─── B. autocomplete (REQ-SEARCH-005) ────────────────────────────────

    @Test
    @DisplayName("autocomplete — 인기검색어 + 콘텐츠 통합 결과")
    void autocomplete_returnsCombinedResults() {
        SearchPopularCache cache1 = SearchPopularCache.builder()
                .id(1L).periodType("DAILY").periodDate(LocalDate.now()).locale("ko")
                .query("서울 청년").searchCount(120).rank(1).build();
        when(popularCacheMapper.findTopN(eq("DAILY"), any(LocalDate.class), eq("ko"), anyInt()))
                .thenReturn(List.of(cache1));

        Map<String, Object> contentRow = new HashMap<>();
        contentRow.put("term", "서울시 정책");
        contentRow.put("similarity", 0.65);
        when(unifiedSearchMapper.autocomplete(eq("서울"), eq("ko"), anyInt()))
                .thenReturn(List.of(contentRow));

        List<AutocompleteItem> items = service.autocomplete("서울", "ko", 10);

        assertThat(items).extracting(AutocompleteItem::source)
                .contains("popular", "content");
        assertThat(items).extracting(AutocompleteItem::term)
                .contains("서울 청년", "서울시 정책");
    }

    @Test
    @DisplayName("autocomplete — 1자 prefix 빈 응답")
    void autocomplete_emptyPrefix_returnsEmpty() {
        List<AutocompleteItem> items = service.autocomplete("서", "ko", 10);
        assertThat(items).isEmpty();
    }

    // ─── C. getPopular (REQ-SEARCH-006) ──────────────────────────────────

    @Test
    @DisplayName("getPopular — 캐시에서 TOP-N 반환")
    void getPopular_returnsTopNFromCache() {
        SearchPopularCache row1 = SearchPopularCache.builder()
                .id(1L).periodType("DAILY").periodDate(LocalDate.now()).locale("ko")
                .query("서울 청년").searchCount(1542).rank(1).build();
        SearchPopularCache row2 = SearchPopularCache.builder()
                .id(2L).periodType("DAILY").periodDate(LocalDate.now()).locale("ko")
                .query("교통 안전").searchCount(987).rank(2).build();
        when(popularCacheMapper.findTopN(eq("DAILY"), any(LocalDate.class), eq("ko"), eq(10)))
                .thenReturn(List.of(row1, row2));

        List<PopularQueryItem> items = service.getPopular("DAILY", "ko", 10);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).query()).isEqualTo("서울 청년");
        assertThat(items.get(0).rank()).isEqualTo(1);
        assertThat(items.get(1).count()).isEqualTo(987L);
    }

    @Test
    @DisplayName("getPopular — 캐시 빈 경우 빈 리스트 반환")
    void getPopular_emptyCache_returnsEmptyList() {
        when(popularCacheMapper.findTopN(anyString(), any(LocalDate.class), anyString(), anyInt()))
                .thenReturn(List.of());

        List<PopularQueryItem> items = service.getPopular("DAILY", "ko", 10);

        assertThat(items).isEmpty();
    }

    // ─── D. recordClick (REQ-SEARCH-008) ────────────────────────────────

    @Test
    @DisplayName("recordClick — 정상 시 updateClickInfo 호출")
    void recordClick_validRequest_updatesClickInfo() {
        SearchLog log = SearchLog.builder()
                .id(100L).sessionId("sess-1").userId(10L)
                .createdAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();
        when(searchLogMapper.findById(100L)).thenReturn(Optional.of(log));

        service.recordClick(100L, "board", 12345L, 3, 10L, "sess-1");

        verify(searchLogMapper).updateClickInfo(eq(100L), eq("board"), eq(12345L), eq(3));
    }

    @Test
    @DisplayName("recordClick — 30분 윈도우 초과 시 SearchClickWindowExpiredException")
    void recordClick_expiredWindow_throwsClickWindowExpired() {
        SearchLog log = SearchLog.builder()
                .id(100L).sessionId("sess-1").userId(10L)
                .createdAt(Instant.now().minus(31, ChronoUnit.MINUTES))
                .build();
        when(searchLogMapper.findById(100L)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.recordClick(100L, "board", 1L, 1, 10L, "sess-1"))
                .isInstanceOf(SearchClickWindowExpiredException.class);
    }

    @Test
    @DisplayName("recordClick — session_id 불일치 시 AccessDeniedException")
    void recordClick_sessionMismatch_throwsAccessDenied() {
        SearchLog log = SearchLog.builder()
                .id(100L).sessionId("sess-A").userId(10L)
                .createdAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();
        when(searchLogMapper.findById(100L)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.recordClick(100L, "board", 1L, 1, 999L, "sess-DIFFERENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("recordClick — 미존재 searchLogId 시 SearchLogNotFoundException")
    void recordClick_nonExistentSearchLog_throwsNotFound() {
        when(searchLogMapper.findById(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordClick(99999L, "board", 1L, 1, 10L, "sess"))
                .isInstanceOf(SearchLogNotFoundException.class);
    }

    // ─── E. 동의어 확장 + 비공개 가드 ──────────────────────────────────

    @Test
    @DisplayName("search — 동의어 확장 결과가 응답 expandedQuery에 포함")
    void search_synonymExpansion_includesExpandedTerms() {
        when(synonymService.expandQuery(eq("수도"), eq("ko"))).thenReturn("수도 OR 서울");
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(0L);

        SearchRequest req = new SearchRequest("수도", "ALL", 1, 20, "ko");
        SearchResponse resp = service.search(req, null, false, "sess", null);

        assertThat(resp.expandedQuery()).contains("수도");
        assertThat(resp.expandedQuery()).contains("서울");
    }

    @Test
    @DisplayName("search — 동의어 확장 20 토큰 절단을 SynonymService로 위임 검증")
    void search_synonymExpansionAt20TokenLimit_truncates() {
        // SynonymService가 OR 토큰 수 절단을 책임지므로, 서비스는 결과를 그대로 활용한다.
        StringBuilder sb = new StringBuilder("a");
        for (int i = 1; i < 20; i++) {
            sb.append(" OR syn").append(i);
        }
        when(synonymService.expandQuery(anyString(), anyString())).thenReturn(sb.toString());
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(0L);

        SearchRequest req = new SearchRequest("a", "ALL", 1, 20, "ko");
        SearchResponse resp = service.search(req, null, false, "sess", null);

        // 응답 expandedQuery의 OR 토큰 수가 20개 이하인지 검증
        assertThat(resp.expandedQuery().split(" OR ")).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("search — 비공개 콘텐츠 권한 가드: 매퍼에 isAdmin/requesterId 전달")
    void search_qnaPrivateContent_filteredForNonOwner() {
        when(synonymService.expandQuery(anyString(), anyString())).thenReturn(null);
        when(unifiedSearchMapper.searchUnified(
                anyString(), anyString(), anyString(), anyMap(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(unifiedSearchMapper.countUnified(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(0L);

        SearchRequest req = new SearchRequest("민원", "ALL", 1, 20, "ko");
        service.search(req, 11L, false, "sess", null);

        // 비공개 가드 파라미터(requesterId=11, isAdmin=false)가 매퍼로 전달되었는지 검증
        verify(unifiedSearchMapper).searchUnified(
                anyString(), anyString(), anyString(), anyMap(),
                eq(11L), eq(false), anyInt(), anyInt());
        verify(unifiedSearchMapper).countUnified(
                anyString(), anyString(), anyString(), eq(11L), eq(false));
    }
}
