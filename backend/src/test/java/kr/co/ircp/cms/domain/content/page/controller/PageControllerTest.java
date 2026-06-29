package kr.co.ircp.cms.domain.content.page.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.page.dto.PageCreateRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.dto.PagePublishRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageResponse;
import kr.co.ircp.cms.domain.content.page.dto.PageScheduleRequest;
import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.service.PageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static kr.co.ircp.cms.support.JwtPrincipalTestFactory.jwtAuth;
import static kr.co.ircp.cms.support.JwtPrincipalTestFactory.withAuthority;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PageController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 이력 HTTP 계층 검증.
 */
@WebMvcTest(PageController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PageController GREEN 테스트 (REQ-CONTENT-005-D)")
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PageService pageService;

    @MockitoBean
    private kr.co.ircp.cms.domain.content.page.service.PageHistoryService pageHistoryService;

    private static PageResponse samplePage(Long id, String slug, String status) {
        return new PageResponse(
                id, 1L, 1L, null, "PAGE-" + id, "페이지 제목", slug, status,
                null, null, null, null, null, null, 1,
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("POST /pages — 생성 201 Created + Location + body")
    void createPage_returnsCreated() throws Exception {
        PageCreateRequest req = new PageCreateRequest(1L, 1L, null, "PAGE-001", "테스트", "test-slug");
        PageResponse created = samplePage(10L, "test-slug", "DRAFT");
        when(pageService.createPage(any(PageCreateRequest.class), any())).thenReturn(created);

        // @AuthenticationPrincipal(expression="userId") SpEL 호환을 위해 JwtPrincipal 인증 토큰 주입
        mockMvc.perform(post("/api/v1/content/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("PAGE:WRITE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.slug").value("test-slug"));
    }

    @Test
    @DisplayName("POST /pages — slug 패턴 위반 시 400 Bad Request")
    void createPage_invalidSlug_returns400() throws Exception {
        // slug에 대문자 포함 — 패턴 위반
        String invalidJson = "{\"siteId\":1,\"templateId\":1,\"code\":\"PAGE-001\",\"title\":\"제목\",\"slug\":\"INVALID_SLUG\"}";

        mockMvc.perform(post("/api/v1/content/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .with(jwtAuth(withAuthority("PAGE:WRITE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /pages/{id} — 수정 200 OK")
    void updatePage_returnsOk() throws Exception {
        PageUpdateRequest req = new PageUpdateRequest(
                "변경된 제목", "new-slug", 1L, null,
                null, null, null, null, null, "수정 사유", 1
        );
        PageResponse updated = samplePage(5L, "new-slug", "DRAFT");
        when(pageService.updatePage(eq(5L), any(PageUpdateRequest.class), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/content/pages/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("PAGE:WRITE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.slug").value("new-slug"));
    }

    @Test
    @DisplayName("POST /pages/{id}/publish — 즉시 발행 200 OK")
    void publishPage_returnsOk() throws Exception {
        PagePublishRequest req = new PagePublishRequest("긴급 공지");
        PageResponse published = samplePage(7L, "notice", "PUBLISHED");
        when(pageService.publishPage(eq(7L), any(), any())).thenReturn(published);

        mockMvc.perform(post("/api/v1/content/pages/7/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("PAGE:PUBLISH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("POST /pages/{id}/schedule — 예약 발행 200 OK")
    void schedulePage_returnsOk() throws Exception {
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        PageScheduleRequest req = new PageScheduleRequest(future);
        PageResponse scheduled = samplePage(8L, "scheduled", "SCHEDULED");
        when(pageService.schedulePage(eq(8L), any(PageScheduleRequest.class), any())).thenReturn(scheduled);

        mockMvc.perform(post("/api/v1/content/pages/8/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("PAGE:PUBLISH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("POST /pages/{id}/retract — 철회 200 OK")
    void retractPage_returnsOk() throws Exception {
        PageResponse retracted = samplePage(9L, "withdrawn", "RETRACTED");
        when(pageService.retractPage(eq(9L), any())).thenReturn(retracted);

        mockMvc.perform(post("/api/v1/content/pages/9/retract")
                        .with(jwtAuth(withAuthority("PAGE:PUBLISH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRACTED"));
    }

    @Test
    @DisplayName("GET /pages/{id}/history — 이력 목록 조회 200 OK")
    void getPageHistory_returnsOk() throws Exception {
        // PAGE:HISTORY:READ 권한은 @PreAuthorize 검증에만 사용 — @AuthenticationPrincipal 미사용
        PageHistoryResponse h1 = new PageHistoryResponse(1L, 3L, 2, "{}", 1L, Instant.now(), "v2");
        PageHistoryResponse h2 = new PageHistoryResponse(2L, 3L, 1, "{}", 1L, Instant.now(), "v1");
        when(pageService.getPageHistory(eq(3L))).thenReturn(List.of(h1, h2));

        mockMvc.perform(get("/api/v1/content/pages/3/history")
                        .with(jwtAuth(withAuthority("PAGE:HISTORY:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
    }

    @Test
    @DisplayName("GET /pages/{id}/history/diff — title·slug diff 200 OK (AC-003-3)")
    void getPageHistoryDiff_returnsOk() throws Exception {
        var slugDiff = new kr.co.ircp.cms.common.dto.RevisionDiffResponse(
                "slug", 1, 2,
                List.of(new kr.co.ircp.cms.common.dto.DiffLine(
                        kr.co.ircp.cms.common.dto.DiffType.DELETE, 1, null, "old-slug"),
                        new kr.co.ircp.cms.common.dto.DiffLine(
                                kr.co.ircp.cms.common.dto.DiffType.INSERT, null, 1, "new-slug")));
        var titleDiff = new kr.co.ircp.cms.common.dto.RevisionDiffResponse(
                "title", 1, 2,
                List.of(new kr.co.ircp.cms.common.dto.DiffLine(
                        kr.co.ircp.cms.common.dto.DiffType.EQUAL, 1, 1, "제목")));
        when(pageHistoryService.diff(eq(3L), eq(1), eq(2)))
                .thenReturn(List.of(slugDiff, titleDiff));

        mockMvc.perform(get("/api/v1/content/pages/3/history/diff")
                        .param("from", "1")
                        .param("to", "2")
                        .with(jwtAuth(withAuthority("PAGE:HISTORY:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].field").value("slug"))
                .andExpect(jsonPath("$[1].field").value("title"));
    }

    @Test
    @DisplayName("GET /pages/{id}/history/diff — 권한 없는 사용자 403 Forbidden")
    void getPageHistoryDiff_noAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/content/pages/3/history/diff")
                        .param("from", "1")
                        .param("to", "2")
                        .with(jwtAuth(withAuthority("PAGE:READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /pages/{id}/rollback/{version} — 롤백 200 OK")
    void rollbackPage_returnsOk() throws Exception {
        PageResponse rolled = samplePage(4L, "rolled", "DRAFT");
        when(pageService.rollbackPage(eq(4L), eq(2), any())).thenReturn(rolled);

        mockMvc.perform(post("/api/v1/content/pages/4/rollback/2")
                        .with(jwtAuth(withAuthority("PAGE:ROLLBACK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /pages/by-slug/{slug} — slug로 발행 페이지 조회 200 OK")
    void getBySlug_returnsOk() throws Exception {
        PageResponse page = samplePage(11L, "published-slug", "PUBLISHED");
        when(pageService.getPublishedPageBySlug(anyLong(), eq("published-slug"))).thenReturn(page);

        mockMvc.perform(get("/api/v1/content/pages/by-slug/published-slug")
                        .param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("published-slug"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("POST /pages/{id}/publish — 권한 없는 사용자 인증 시 403 Forbidden")
    void publishPage_unauthenticated_returns403() throws Exception {
        // 익명 사용자는 @AuthenticationPrincipal(expression="userId") SpEL 평가 단계에서
        // String 주체로 인해 ServletException 발생 — IT 레이어에서 익명 401/403을 검증한다.
        // 슬라이스에서는 "권한 없는 인증 사용자(PAGE:READ 만 보유)" 로 403 응답을 검증한다.
        PagePublishRequest req = new PagePublishRequest("긴급 공지");

        mockMvc.perform(post("/api/v1/content/pages/7/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("PAGE:READ"))))
                .andExpect(status().isForbidden());
    }
}
