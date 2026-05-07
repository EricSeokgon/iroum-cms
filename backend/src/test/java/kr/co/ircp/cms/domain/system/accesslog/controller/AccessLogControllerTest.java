package kr.co.ircp.cms.domain.system.accesslog.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogResponse;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogSearchRequest;
import kr.co.ircp.cms.domain.system.accesslog.service.AccessLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AccessLogController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-001-D: 접속 로그 검색+페이징 조회 HTTP 계층 검증.
 *
 * <p>주의: AccessLogService는 WebMvcTestInfraConfig에서 Mock으로 등록되므로
 * 본 테스트 클래스에서는 Autowire하여 stubbing한다.
 */
@WebMvcTest(AccessLogController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("AccessLogController GREEN 테스트 (REQ-SYSTEM-001-D)")
class AccessLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // AccessLogService는 WebMvcTestInfraConfig에서 @MockitoBean으로 등록됨 — 동일 빈을 주입받아 stubbing
    @Autowired
    private AccessLogService accessLogService;

    private static AccessLogResponse sample(Long id, String pageUrl, Integer statusCode) {
        return AccessLogResponse.builder()
                .id(id)
                .siteId(1L)
                .userId(100L)
                .sessionId("session-" + id)
                .ipHash("ipHash-" + id)
                .userAgent("Mozilla/5.0")
                .referrer("https://referrer.example")
                .pageUrl(pageUrl)
                .statusCode(statusCode)
                .responseTimeMs(120)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:LOG:READ"})
    @DisplayName("GET /access-logs — 기본 조회 200 OK + 페이징 응답")
    void list_returnsOkWithPagedItems() throws Exception {
        when(accessLogService.search(any(AccessLogSearchRequest.class))).thenReturn(List.of(
                sample(1L, "/home", 200),
                sample(2L, "/about", 200)
        ));
        when(accessLogService.count(any(AccessLogSearchRequest.class))).thenReturn(2L);

        mockMvc.perform(get("/api/v1/system/access-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:LOG:READ"})
    @DisplayName("GET /access-logs?page=1&size=10 — 페이징 파라미터 반영 200 OK")
    void list_withPagination_returnsOk() throws Exception {
        when(accessLogService.search(any(AccessLogSearchRequest.class))).thenReturn(List.of(
                sample(11L, "/contact", 200)
        ));
        when(accessLogService.count(any(AccessLogSearchRequest.class))).thenReturn(25L);

        mockMvc.perform(get("/api/v1/system/access-logs")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(25));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:LOG:READ"})
    @DisplayName("GET /access-logs?from=2026-05-01&to=2026-05-07&statusCode=500 — 필터 조회 200 OK")
    void list_withFilters_returnsOk() throws Exception {
        when(accessLogService.search(any(AccessLogSearchRequest.class))).thenReturn(List.of(
                sample(20L, "/error-page", 500)
        ));
        when(accessLogService.count(any(AccessLogSearchRequest.class))).thenReturn(1L);

        mockMvc.perform(get("/api/v1/system/access-logs")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-07")
                        .param("statusCode", "500")
                        .param("pageUrl", "/error-page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].statusCode").value(500))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:LOG:READ"})
    @DisplayName("GET /access-logs — 결과 없음 200 OK + 빈 배열")
    void list_emptyResult_returnsOkWithEmpty() throws Exception {
        when(accessLogService.search(any(AccessLogSearchRequest.class))).thenReturn(List.of());
        when(accessLogService.count(any(AccessLogSearchRequest.class))).thenReturn(0L);

        mockMvc.perform(get("/api/v1/system/access-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }
}
