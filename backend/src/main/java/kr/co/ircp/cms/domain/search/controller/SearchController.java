package kr.co.ircp.cms.domain.search.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.search.dto.AutocompleteItem;
import kr.co.ircp.cms.domain.search.dto.ClickRequest;
import kr.co.ircp.cms.domain.search.dto.PopularQueryItem;
import kr.co.ircp.cms.domain.search.dto.SearchRequest;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.dto.SearchStatsResponse;
import kr.co.ircp.cms.domain.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;

/**
 * 통합 검색 REST 컨트롤러.
 *
 * <p>SPEC-CMS-010 §6.1~6.4: 검색·자동완성·인기 검색어·클릭 추적 (PUBLIC).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 통합 검색 컨트롤러 (4개 엔드포인트)
// @MX:SPEC: SPEC-CMS-010#REQ-SEARCH-001
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "domain", required = false) String domain,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            @RequestParam(name = "locale", required = false, defaultValue = "ko") String locale,
            @AuthenticationPrincipal Long requesterId,
            HttpServletRequest httpRequest
    ) {
        boolean isAdmin = currentUserHasRole("ADMIN");
        String sessionId = resolveSessionId(httpRequest);
        String ipHash = hashIp(httpRequest.getRemoteAddr());
        SearchRequest req = new SearchRequest(q, domain, page, size, locale);
        SearchResponse resp = searchService.search(req, requesterId, isAdmin, sessionId, ipHash);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<AutocompleteItem>> autocomplete(
            @RequestParam(name = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit,
            @RequestParam(name = "locale", required = false, defaultValue = "ko") String locale
    ) {
        return ResponseEntity.ok(searchService.autocomplete(prefix, locale, limit));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<PopularQueryItem>> getPopular(
            @RequestParam(name = "period", required = false, defaultValue = "DAILY") String period,
            @RequestParam(name = "locale", required = false, defaultValue = "ko") String locale,
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(searchService.getPopular(period, locale, limit));
    }

    @PostMapping("/click")
    public ResponseEntity<Void> recordClick(
            @Valid @RequestBody ClickRequest req,
            @AuthenticationPrincipal Long requesterId,
            HttpServletRequest httpRequest
    ) {
        String sessionId = resolveSessionId(httpRequest);
        searchService.recordClick(req.searchLogId(), req.docType(), req.docId(), req.rank(),
                requesterId, sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 운영자용 검색 통계 (REQ-SEARCH-008 §6.6).
     * ADMIN 전용. from/to 미지정 시 최근 7일.
     */
    @GetMapping("/stats/queries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SearchStatsResponse> stats(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    ) {
        LocalDate fromDate = parseLocalDate(from);
        LocalDate toDate = parseLocalDate(to);
        return ResponseEntity.ok(searchService.getStats(fromDate, toDate, limit));
    }

    private static LocalDate parseLocalDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            // 잘못된 형식은 무시 — null 처리(서비스가 default 적용)
            return null;
        }
    }

    private static boolean currentUserHasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role) || a.getAuthority().equals(role));
    }

    private static String resolveSessionId(HttpServletRequest request) {
        // Cookie 기반 session_id가 있으면 활용. 없으면 JSESSIONID, 마지막은 anonymous.
        if (request == null) return "anonymous";
        String header = request.getHeader("X-Session-Id");
        if (header != null && !header.isBlank()) return header;
        if (request.getSession(false) != null) return request.getSession(false).getId();
        return "anonymous";
    }

    private static String hashIp(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return null;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(remoteAddr.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
