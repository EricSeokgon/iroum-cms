package kr.co.ircp.cms.domain.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.search.dto.SynonymCreateRequest;
import kr.co.ircp.cms.domain.search.dto.SynonymUpdateRequest;
import kr.co.ircp.cms.domain.search.entity.SearchSynonym;
import kr.co.ircp.cms.domain.search.exception.DuplicateSynonymException;
import kr.co.ircp.cms.domain.search.exception.SynonymSelfException;
import kr.co.ircp.cms.domain.search.service.SynonymService;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SynonymController GREEN 단계 테스트.
 * SPEC-CMS-010 REQ-SEARCH-009: 동의어 사전 CRUD HTTP 계층 검증.
 *
 * <p>모든 엔드포인트가 클래스 레벨 @PreAuthorize("hasRole('ADMIN')") 적용 — 인증 필수.
 */
@WebMvcTest(SynonymController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SynonymController GREEN 테스트 (REQ-SEARCH-009)")
class SynonymControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SynonymService synonymService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("ADMIN"));

    private SearchSynonym sampleSynonym(Long id) {
        return SearchSynonym.builder()
                .id(id)
                .term("수도")
                .synonym("서울")
                .locale("ko")
                .status("ACTIVE")
                .description("행정수도")
                .createdBy(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/search/synonyms — ADMIN 인증 시 200 OK + 페이징 응답")
    void list_returns200WithPage_whenAdmin() throws Exception {
        // given
        PageResponse<SearchSynonym> page = PageResponse.of(
                List.of(sampleSynonym(1L)), 0, 20, 1L);
        when(synonymService.listSynonyms(anyString(), anyInt(), anyInt())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/search/synonyms")
                        .param("locale", "ko")
                        .param("page", "0")
                        .param("size", "20")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].term").value("수도"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/search/synonyms — ADMIN 인증 시 201 Created + 등록된 동의어 반환")
    void create_validRequest_returns201_whenAdmin() throws Exception {
        // given
        when(synonymService.createSynonym(any(), any())).thenReturn(sampleSynonym(5L));
        SynonymCreateRequest req = new SynonymCreateRequest("수도", "서울", "ko", "행정수도");

        // when & then
        mockMvc.perform(post("/api/v1/search/synonyms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.synonym").value("서울"));
    }

    @Test
    @DisplayName("POST /api/v1/search/synonyms — term=synonym 시 400 + SEARCH_SYNONYM_SELF")
    void create_termEqualsSynonym_returns400() throws Exception {
        // given
        when(synonymService.createSynonym(any(), any())).thenThrow(new SynonymSelfException());
        SynonymCreateRequest req = new SynonymCreateRequest("서울", "서울", "ko", null);

        // when & then
        mockMvc.perform(post("/api/v1/search/synonyms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_SYNONYM_SELF"));
    }

    @Test
    @DisplayName("POST /api/v1/search/synonyms — 중복 등록 시 409 + SEARCH_SYNONYM_DUPLICATE")
    void create_duplicate_returns409() throws Exception {
        // given
        when(synonymService.createSynonym(any(), any()))
                .thenThrow(new DuplicateSynonymException("수도", "서울", "ko"));
        SynonymCreateRequest req = new SynonymCreateRequest("수도", "서울", "ko", null);

        // when & then
        mockMvc.perform(post("/api/v1/search/synonyms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEARCH_SYNONYM_DUPLICATE"));
    }

    @Test
    @DisplayName("PUT /api/v1/search/synonyms/{id} — ADMIN 인증 시 200 OK + 수정된 동의어 반환")
    void update_existing_returns200_whenAdmin() throws Exception {
        // given
        when(synonymService.updateSynonym(eq(1L), any(), any())).thenReturn(sampleSynonym(1L));
        SynonymUpdateRequest req = new SynonymUpdateRequest("서울특별시", "ACTIVE");

        // when & then
        mockMvc.perform(put("/api/v1/search/synonyms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/search/synonyms/{id} — ADMIN 인증 시 204 No Content")
    void delete_existing_returns204_whenAdmin() throws Exception {
        // given
        doNothing().when(synonymService).deleteSynonym(anyLong(), any());

        // when & then
        mockMvc.perform(delete("/api/v1/search/synonyms/1")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isNoContent());
    }

    /**
     * JwtPrincipal 기반 인증 토큰 헬퍼.
     * JwtPrincipal.getAuthorities() 가 ROLE_ prefix 자동 부여 → 그대로 사용.
     */
    private UsernamePasswordAuthenticationToken jwtAuth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.getAuthorities().stream()
                        .map(a -> (GrantedAuthority) a)
                        .toList());
    }
}
