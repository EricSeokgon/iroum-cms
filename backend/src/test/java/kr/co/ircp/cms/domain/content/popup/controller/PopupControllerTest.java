package kr.co.ircp.cms.domain.content.popup.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.popup.dto.PopupActiveResponse;
import kr.co.ircp.cms.domain.content.popup.dto.PopupRequest;
import kr.co.ircp.cms.domain.content.popup.dto.PopupResponse;
import kr.co.ircp.cms.domain.content.popup.service.PopupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PopupController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-008-D: 팝업 CRUD + 활성 팝업 조회 HTTP 계층 검증.
 */
@WebMvcTest(PopupController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PopupController GREEN 테스트 (REQ-CONTENT-008-D)")
class PopupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PopupService popupService;

    private static PopupResponse samplePopup(Long id) {
        return new PopupResponse(
                id, 1L, "팝업 제목", "팝업 제목", "<p>HTML</p>", "CENTER", 400, null, 300, null,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                false, 1, "ALL", List.of("ALL"), "ACTIVE", true,
                Instant.now(), Instant.now()
        );
    }

    private static PopupActiveResponse sampleActive(Long id) {
        return new PopupActiveResponse(
                id, "제목", "<p>내용</p>", "CENTER", null, null, 400, 300,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                false, null, 1, "ALL", List.of()
        );
    }

    @Test
    @DisplayName("GET /popups/active — 활성 팝업 200 OK + X-Popup-Limit 헤더 (PUBLIC)")
    void getActivePopups_returnsOkWithHeader() throws Exception {
        when(popupService.getActivePopups(eq(1L)))
                .thenReturn(List.of(sampleActive(1L), sampleActive(2L)));

        mockMvc.perform(get("/api/v1/content/popups/active").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Popup-Limit", "5"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:READ"})
    @DisplayName("GET /popups — 사이트별 전체 팝업 200 OK")
    void getPopupsBySite_returnsOk() throws Exception {
        when(popupService.getPopupsBySite(eq(1L)))
                .thenReturn(List.of(samplePopup(1L)));

        mockMvc.perform(get("/api/v1/content/popups").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("POST /popups — 등록 200 OK")
    void registerPopup_returnsOk() throws Exception {
        PopupRequest req = new PopupRequest(
                1L, "팝업", "<p>내용</p>", "CENTER", null, null, 400, 300,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                false, 1, "ALL", null
        );
        when(popupService.registerPopup(any(PopupRequest.class))).thenReturn(samplePopup(10L));

        mockMvc.perform(post("/api/v1/content/popups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("PUT /popups/{id} — 수정 200 OK")
    void updatePopup_returnsOk() throws Exception {
        PopupRequest req = new PopupRequest(
                1L, "변경", "<p>변경</p>", "CENTER", null, null, 400, 300,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                true, 2, "ALL", null
        );
        when(popupService.updatePopup(eq(5L), any(PopupRequest.class))).thenReturn(samplePopup(5L));

        mockMvc.perform(put("/api/v1/content/popups/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("DELETE /popups/{id} — 삭제 204 No Content")
    void deletePopup_returnsNoContent() throws Exception {
        doNothing().when(popupService).deletePopup(eq(5L));

        mockMvc.perform(delete("/api/v1/content/popups/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /popups — 인증 없이 접근 시 403 Forbidden")
    void createPopup_unauthenticated_returns403() throws Exception {
        // @Valid 통과 후 @PreAuthorize에서 403이 반환되어야 하므로 모든 필수 필드를 채운다.
        PopupRequest req = new PopupRequest(
                1L, "팝업", "<p>내용</p>", "CENTER", null, null, 400, 300,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                false, 1, "ALL", null
        );
        mockMvc.perform(post("/api/v1/content/popups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
