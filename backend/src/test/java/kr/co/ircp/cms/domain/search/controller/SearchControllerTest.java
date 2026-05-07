package kr.co.ircp.cms.domain.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.search.dto.AutocompleteItem;
import kr.co.ircp.cms.domain.search.dto.ClickRequest;
import kr.co.ircp.cms.domain.search.dto.PopularQueryItem;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.dto.SearchStatsResponse;
import kr.co.ircp.cms.domain.search.exception.SearchClickWindowExpiredException;
import kr.co.ircp.cms.domain.search.exception.SearchDomainInvalidException;
import kr.co.ircp.cms.domain.search.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SearchController GREEN 단계 테스트.
 * SPEC-CMS-010 REQ-SEARCH-001/002/004/005/006/008: 통합 검색 HTTP 계층 검증.
 */
@WebMvcTest(SearchController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SearchController GREEN 테스트 (REQ-SEARCH-001~008)")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Autowired
    private ObjectMapper objectMapper;

    // 운영자 통계 엔드포인트(@PreAuthorize("hasRole('ADMIN')")) 검증용 Principal
    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("ADMIN"));

    @Test
    @DisplayName("GET /api/v1/search — 200 OK, 검색 결과 반환")
    void search_validParams_returns200WithResults() throws Exception {
        // given
        SearchResponse resp = new SearchResponse(
                0, 0, List.of(), Map.of("ALL", 0L), "검색어"
        );
        when(searchService.search(any(), any(), anyBoolean(), anyString(), any()))
                .thenReturn(resp);

        // when & then — 공개 엔드포인트
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "검색어")
                        .param("domain", "ALL")
                        .param("page", "1")
                        .param("size", "20")
                        .param("locale", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.expandedQuery").value("검색어"));
    }

    @Test
    @DisplayName("GET /api/v1/search — 잘못된 domain 시 400 + 에러 코드")
    void search_invalidDomain_returns400() throws Exception {
        // given
        when(searchService.search(any(), any(), anyBoolean(), anyString(), any()))
                .thenThrow(new SearchDomainInvalidException("invalid_domain"));

        // when & then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "키워드")
                        .param("domain", "invalid_domain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_DOMAIN_INVALID"))
                .andExpect(jsonPath("$.domain").value("invalid_domain"));
    }

    @Test
    @DisplayName("GET /api/v1/search/autocomplete — 200 OK, 제안 목록 반환")
    void autocomplete_validPrefix_returns200WithList() throws Exception {
        // given
        AutocompleteItem item = new AutocompleteItem("서울특별시", 0.85, "popular");
        when(searchService.autocomplete(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(item));

        // when & then
        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "서울")
                        .param("limit", "10")
                        .param("locale", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].term").value("서울특별시"))
                .andExpect(jsonPath("$[0].source").value("popular"));
    }

    @Test
    @DisplayName("GET /api/v1/search/popular — 200 OK, 인기 검색어 반환")
    void popular_validPeriod_returns200() throws Exception {
        // given
        PopularQueryItem item = new PopularQueryItem("청년창업지원", 100L, 1);
        when(searchService.getPopular(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(item));

        // when & then
        mockMvc.perform(get("/api/v1/search/popular")
                        .param("period", "DAILY")
                        .param("locale", "ko")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].query").value("청년창업지원"))
                .andExpect(jsonPath("$[0].count").value(100))
                .andExpect(jsonPath("$[0].rank").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/search/click — 204 No Content, 클릭 추적 성공")
    void click_validRequest_returns204() throws Exception {
        // given
        doNothing().when(searchService).recordClick(
                anyLong(), anyString(), anyLong(), any(), any(), anyString());
        ClickRequest req = new ClickRequest(100L, "BOARD", 42L, 3);

        // when & then
        mockMvc.perform(post("/api/v1/search/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/search/click — 30분 윈도우 만료 시 410 Gone")
    void click_invalidWindow_returns410() throws Exception {
        // given
        doThrow(new SearchClickWindowExpiredException(999L))
                .when(searchService).recordClick(
                        anyLong(), anyString(), anyLong(), any(), any(), anyString());
        ClickRequest req = new ClickRequest(999L, "BOARD", 42L, 1);

        // when & then
        mockMvc.perform(post("/api/v1/search/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SEARCH_CLICK_WINDOW_EXPIRED"))
                .andExpect(jsonPath("$.searchLogId").value(999));
    }

    @Test
    @DisplayName("GET /api/v1/search/stats/queries — EDITOR 역할 시 403")
    void getQueryStats_returns403_whenEditorRole() throws Exception {
        JwtPrincipal editor = new JwtPrincipal(99L, "editor", Set.of("EDITOR"));
        mockMvc.perform(get("/api/v1/search/stats/queries")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(editor))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/v1/search/stats/queries — ADMIN 인증 시 200 OK + 통계 반환")
    void stats_returns200WithStats_whenAdmin() throws Exception {
        // given
        SearchStatsResponse stats = new SearchStatsResponse(
                List.of(Map.of("query", "정책", "count", 50L)),
                0.05, 120.5, 1000L
        );
        when(searchService.getStats(isNull(), isNull(), eq(20)))
                .thenReturn(stats);

        // when & then — ADMIN 인증으로 접근
        mockMvc.perform(get("/api/v1/search/stats/queries")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSearches").value(1000))
                .andExpect(jsonPath("$.zeroResultRatio").value(0.05))
                .andExpect(jsonPath("$.avgResponseMs").value(120.5));
    }

    /**
     * JwtPrincipal 기반 인증 토큰 헬퍼.
     *
     * <p>JwtPrincipal.getAuthorities() 는 roles 에 ROLE_ prefix 를 자동으로 붙인 권한을 반환하므로,
     * 여기서는 그대로 사용한다.
     */
    private UsernamePasswordAuthenticationToken jwtAuth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.getAuthorities().stream()
                        .map(a -> (GrantedAuthority) a)
                        .toList());
    }
}
