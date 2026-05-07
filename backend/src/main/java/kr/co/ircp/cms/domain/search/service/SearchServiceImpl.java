package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.search.dto.AutocompleteItem;
import kr.co.ircp.cms.domain.search.dto.DocResult;
import kr.co.ircp.cms.domain.search.dto.PopularQueryItem;
import kr.co.ircp.cms.domain.search.dto.SearchRequest;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.dto.SearchStatsResponse;
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
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 통합 검색 서비스 구현체.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001~008.
 * - search: 정규화 → 동의어 확장(20 토큰 절단) → UnifiedSearchMapper → ts_headline sanitize → SearchLog 적재
 * - autocomplete: search_popular_cache(prefix) + UnifiedSearchMapper.autocomplete 통합 정렬
 * - getPopular: SearchPopularCacheMapper.findTopN
 * - recordClick: 30분 윈도우 + session/user 매칭 검증
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 통합 검색 서비스 구현 (REQ-SEARCH-001~008)
// @MX:ANCHOR: [AUTO] SearchServiceImpl — SearchController가 4개 엔드포인트에서 호출 (fan_in >= 4)
// @MX:REASON: 통합 검색은 6개 도메인 데이터의 단일 진입점. ts_headline sanitize/비공개 가드 무회귀 critical
// @MX:SPEC: SPEC-CMS-010#REQ-SEARCH-001
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    /** 검색 쿼리 최대 길이 (REQ-SEARCH-001) */
    private static final int MAX_QUERY_LENGTH = 200;

    /** 클릭 추적 윈도우 — 30분(REQ-SEARCH-008) */
    private static final long CLICK_WINDOW_MINUTES = 30L;

    /** 도메인 화이트리스트 (REQ-SEARCH-004) */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "ALL", "board", "content", "policy", "safety", "media", "publication"
    );

    /** locale 화이트리스트 (REQ-SEARCH-010) */
    private static final Set<String> ALLOWED_LOCALES = Set.of("ko", "en");

    /** 도메인별 가중치 (REQ-SEARCH-001 §5.1) */
    private static final Map<String, Double> DOMAIN_WEIGHTS = Map.of(
            "board", 1.0,
            "content", 0.9,
            "publication", 0.85,
            "policy", 0.8,
            "safety", 0.7,
            "media", 0.5
    );

    /** ts_headline 출력 sanitize — <mark> 태그만 허용 (REQ-SEARCH-002) */
    // @MX:WARN: [AUTO] sanitize 정책 변경 시 OWASP XSS 우회 가능 — 변경은 보안 검토 필수
    // @MX:REASON: ts_headline 결과는 사용자 콘텐츠를 포함 — XSS 페이로드가 mark 태그 외부에 있을 수 있음
    private static final Safelist HIGHLIGHT_SAFELIST = Safelist.none().addTags("mark");

    private final UnifiedSearchMapper unifiedSearchMapper;
    private final SearchPopularCacheMapper popularCacheMapper;
    private final SearchLogMapper searchLogMapper;
    private final SynonymService synonymService;

    @Override
    public SearchResponse search(SearchRequest req, Long requesterId, boolean isAdmin,
                                  String sessionId, String ipHash) {
        long startNanos = System.nanoTime();

        // 1) 입력 검증 (REQ-SEARCH-001/004/010)
        String rawQuery = req.query() == null ? "" : req.query();
        if (rawQuery.length() > MAX_QUERY_LENGTH) {
            throw new SearchQueryTooLongException(rawQuery.length(), MAX_QUERY_LENGTH);
        }
        String domain = (req.domain() == null || req.domain().isBlank()) ? "ALL" : req.domain();
        if (!ALLOWED_DOMAINS.contains(domain)) {
            throw new SearchDomainInvalidException(domain);
        }
        String locale = (req.locale() == null || req.locale().isBlank()) ? "ko" : req.locale();
        if (!ALLOWED_LOCALES.contains(locale)) {
            throw new SearchLocaleUnsupportedException(locale);
        }
        int page = Math.max(1, req.page());
        int size = Math.min(50, Math.max(1, req.size()));

        // 2) 쿼리 정규화 — 소문자 + 공백 collapse
        String normalized = normalize(rawQuery);

        // 3) 동의어 확장 (REQ-SEARCH-009)
        String expandedQuery = synonymService.expandQuery(normalized, locale);
        String tsQueryInput = (expandedQuery == null || expandedQuery.isBlank()) ? normalized : expandedQuery;

        // 4) 빈 쿼리 처리
        if (normalized.isBlank()) {
            return new SearchResponse(0, 0, List.of(), Map.of(), normalized);
        }

        // 5) UnifiedSearchMapper 호출
        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = unifiedSearchMapper.searchUnified(
                tsQueryInput, locale, domain, DOMAIN_WEIGHTS, requesterId, isAdmin, offset, size
        );
        long total = unifiedSearchMapper.countUnified(
                tsQueryInput, locale, domain, requesterId, isAdmin
        );

        // 6) ts_headline sanitize + DocResult 변환 (REQ-SEARCH-002)
        List<DocResult> content = new ArrayList<>(rows.size());
        Map<String, Long> facets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String snippet = sanitizeHighlight((String) row.get("snippet"));
            DocResult doc = new DocResult(
                    (String) row.get("doc_type"),
                    asLong(row.get("doc_id")),
                    (String) row.get("title"),
                    snippet,
                    asDouble(row.get("rank")),
                    (String) row.get("domain"),
                    (String) row.get("url"),
                    asInstant(row.get("created_at"))
            );
            content.add(doc);
            facets.merge(doc.domain(), 1L, Long::sum);
        }

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);

        // 7) 비동기 검색 로그 적재 (REQ-SEARCH-008) — 응답 직전, 트랜잭션 후 적재가 이상적이나
        //    Step 2 범위에서는 동기 INSERT로 처리(@Async 도입은 후속 트랙). 응답 시간 영향은 < 10ms 가정.
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        try {
            insertSearchLog(rawQuery, normalized, expandedQuery, (int) total,
                    (int) elapsedMs, locale, domain, requesterId, sessionId, ipHash);
        } catch (Exception ignored) {
            // 로깅 실패가 검색 결과 노출을 막아서는 안 됨
        }

        return new SearchResponse((int) total, totalPages, content, facets, expandedQuery);
    }

    @Override
    public List<AutocompleteItem> autocomplete(String prefix, String locale, int limit) {
        if (prefix == null || prefix.length() < 2) {
            return List.of();
        }
        if (prefix.length() > 50) {
            throw new SearchQueryTooLongException(prefix.length(), 50);
        }
        String resolvedLocale = (locale == null || locale.isBlank()) ? "ko" : locale;
        if (!ALLOWED_LOCALES.contains(resolvedLocale)) {
            throw new SearchLocaleUnsupportedException(resolvedLocale);
        }
        int safeLimit = Math.min(20, Math.max(1, limit));

        // 1) 인기검색어 prefix 매칭 — 가장 최근 DAILY 캐시 활용 (간단화)
        List<SearchPopularCache> popularRows = popularCacheMapper.findTopN(
                "DAILY", LocalDate.now(), resolvedLocale, safeLimit * 5
        );
        List<AutocompleteItem> popularItems = new ArrayList<>();
        for (SearchPopularCache row : popularRows) {
            if (row.getQuery() != null && row.getQuery().startsWith(prefix)) {
                popularItems.add(new AutocompleteItem(row.getQuery(), 1.0, "popular"));
            }
        }

        // 2) 콘텐츠 제목 prefix 매칭
        List<Map<String, Object>> contentRows = unifiedSearchMapper.autocomplete(prefix, resolvedLocale, safeLimit);
        List<AutocompleteItem> contentItems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AutocompleteItem p : popularItems) {
            seen.add(p.term());
        }
        for (Map<String, Object> row : contentRows) {
            String term = (String) row.get("term");
            if (term == null || seen.contains(term)) continue;
            seen.add(term);
            contentItems.add(new AutocompleteItem(term, asDouble(row.get("similarity")), "content"));
        }

        // 3) 통합 + score 내림차순 + limit
        List<AutocompleteItem> merged = new ArrayList<>(popularItems.size() + contentItems.size());
        merged.addAll(popularItems);
        merged.addAll(contentItems);
        merged.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        if (merged.size() > safeLimit) {
            merged = merged.subList(0, safeLimit);
        }
        return merged;
    }

    @Override
    public List<PopularQueryItem> getPopular(String periodType, String locale, int limit) {
        String resolvedPeriod = (periodType == null || periodType.isBlank()) ? "DAILY" : periodType;
        String resolvedLocale = (locale == null || locale.isBlank()) ? "ko" : locale;
        if (!ALLOWED_LOCALES.contains(resolvedLocale)) {
            throw new SearchLocaleUnsupportedException(resolvedLocale);
        }
        int safeLimit = Math.min(50, Math.max(1, limit));
        List<SearchPopularCache> rows = popularCacheMapper.findTopN(
                resolvedPeriod, LocalDate.now(), resolvedLocale, safeLimit
        );
        List<PopularQueryItem> items = new ArrayList<>(rows.size());
        for (SearchPopularCache row : rows) {
            items.add(new PopularQueryItem(row.getQuery(), row.getSearchCount(), row.getRank()));
        }
        return items;
    }

    @Override
    @Transactional
    public void recordClick(Long searchLogId, String docType, Long docId, Integer rank,
                             Long requesterId, String sessionId) {
        SearchLog logEntry = searchLogMapper.findById(searchLogId)
                .orElseThrow(() -> new SearchLogNotFoundException(searchLogId));

        // 30분 윈도우 검증 (REQ-SEARCH-008)
        Instant createdAt = logEntry.getCreatedAt();
        if (createdAt != null) {
            long minutes = ChronoUnit.MINUTES.between(createdAt, Instant.now());
            if (minutes > CLICK_WINDOW_MINUTES) {
                throw new SearchClickWindowExpiredException(searchLogId);
            }
        }

        // session_id / user_id 매칭 검증 (RISK-S-07)
        boolean sessionMatch = sessionId != null && sessionId.equals(logEntry.getSessionId());
        boolean userMatch = requesterId != null && Objects.equals(requesterId, logEntry.getUserId());
        if (!sessionMatch && !userMatch) {
            throw new AccessDeniedException("검색 클릭 추적 권한이 없습니다");
        }

        searchLogMapper.updateClickInfo(searchLogId, docType, docId, rank);
    }

    @Override
    public SearchStatsResponse getStats(LocalDate from, LocalDate to, int limit) {
        // 기본값: 최근 7일
        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = (to == null) ? today : to;
        LocalDate effectiveFrom = (from == null) ? effectiveTo.minusDays(7) : from;
        int effectiveLimit = Math.min(100, Math.max(1, limit));

        List<Map<String, Object>> topQueries = unifiedSearchMapper.topQueries(
                effectiveFrom, effectiveTo, effectiveLimit
        );
        Double zeroRatio = unifiedSearchMapper.zeroResultRatio(effectiveFrom, effectiveTo);
        Double avgMs = unifiedSearchMapper.avgResponseMs(effectiveFrom, effectiveTo);
        long totalCount = unifiedSearchMapper.totalSearchCount(effectiveFrom, effectiveTo);

        return new SearchStatsResponse(
                topQueries == null ? List.of() : topQueries,
                zeroRatio == null ? 0.0 : zeroRatio,
                avgMs == null ? 0.0 : avgMs,
                totalCount
        );
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    /** 쿼리 정규화: 소문자 + 트림 + 공백 collapse */
    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** ts_headline 결과 sanitize — &lt;mark&gt; 태그만 허용 */
    private String sanitizeHighlight(String html) {
        if (html == null || html.isBlank()) return html;
        return Jsoup.clean(html, HIGHLIGHT_SAFELIST);
    }

    private void insertSearchLog(String rawQuery, String normalized, String expanded,
                                  int resultCount, int responseMs, String locale,
                                  String domainFilter, Long userId, String sessionId, String ipHash) {
        SearchLog entry = SearchLog.builder()
                .userId(userId)
                .sessionId(sessionId == null ? "anonymous" : sessionId)
                .query(rawQuery)
                .normalizedQuery(normalized)
                .expandedQuery(expanded)
                .resultCount(resultCount)
                .responseMs(responseMs)
                .locale(locale)
                .domainFilter(domainFilter)
                .ipHash(ipHash)
                .build();
        searchLogMapper.insert(entry);
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long l) return l;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static double asDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Double d) return d;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    private static Instant asInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.util.Date d) return d.toInstant();
        return null;
    }

    /** 쿼리 정규화 진단용 (테스트). */
    static String normalizeForTest(String raw) {
        return normalize(raw);
    }
}
