package kr.co.ircp.cms.domain.notification.template.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateCreateRequest;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplatePreviewResult;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateResponse;
import kr.co.ircp.cms.domain.notification.template.admin.exception.NotificationTemplateNotFoundException;
import kr.co.ircp.cms.domain.notification.template.admin.service.NotificationTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static kr.co.ircp.cms.support.JwtPrincipalTestFactory.jwtAuth;
import static kr.co.ircp.cms.support.JwtPrincipalTestFactory.withAuthority;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NotificationTemplateAdminController 테스트 (RED → GREEN).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — HTTP 계층 + @PreAuthorize 권한 게이트 검증.
 */
@WebMvcTest(NotificationTemplateAdminController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("NotificationTemplateAdminController (SPEC-CMS-NOTI-EXT-001)")
class NotificationTemplateAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationTemplateService templateService;

    private static NotificationTemplateResponse sample() {
        return new NotificationTemplateResponse(
                1L, "POLICY_OPEN", "정책 공개", "EMAIL",
                "제목", "<p>본문</p>", "[\"policyName\"]", "ko",
                true, null, 1L, 1L, null, null);
    }

    @Test
    @DisplayName("POST — 201 Created (NOTIFICATION_TEMPLATE:WRITE)")
    void create_returnsCreated() throws Exception {
        var req = new NotificationTemplateCreateRequest(
                "POLICY_OPEN", "정책 공개", "EMAIL", "제목", "<p>본문</p>",
                "[\"policyName\"]", "ko", true, null);
        when(templateService.create(any(), anyLong())).thenReturn(sample());

        mockMvc.perform(post("/api/v1/notification/admin/template")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("POLICY_OPEN"));
    }

    @Test
    @DisplayName("POST — 권한 없으면 403 Forbidden")
    void create_withoutAuthority_returns403() throws Exception {
        var req = new NotificationTemplateCreateRequest(
                "POLICY_OPEN", "정책 공개", "EMAIL", "제목", "<p>본문</p>",
                "[\"policyName\"]", "ko", true, null);

        mockMvc.perform(post("/api/v1/notification/admin/template")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET 목록 — 200 + 페이지 구조 (NOTIFICATION_TEMPLATE:READ)")
    void list_returnsPagedResponse() throws Exception {
        when(templateService.getAll(eq(null), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(sample()), 0, 20, 1L));

        mockMvc.perform(get("/api/v1/notification/admin/template")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @DisplayName("GET 단건 — 200 (NOTIFICATION_TEMPLATE:READ)")
    void detail_returnsOk() throws Exception {
        when(templateService.getById(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/v1/notification/admin/template/1")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET 단건 — 미존재 시 404")
    void detail_notFound_returns404() throws Exception {
        when(templateService.getById(99L))
                .thenThrow(new NotificationTemplateNotFoundException("없음"));

        mockMvc.perform(get("/api/v1/notification/admin/template/99")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT — 200 (NOTIFICATION_TEMPLATE:WRITE)")
    void update_returnsOk() throws Exception {
        when(templateService.update(eq(1L), any(), anyLong())).thenReturn(sample());

        String body = "{\"name\":\"수정\",\"channel\":\"INAPP\"}";
        mockMvc.perform(put("/api/v1/notification/admin/template/1")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE — 204 (NOTIFICATION_TEMPLATE:DELETE)")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/notification/admin/template/1")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:DELETE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST preview — 200 치환 결과 (NOTIFICATION_TEMPLATE:READ)")
    void preview_returnsResult() throws Exception {
        when(templateService.previewTemplate(eq(1L), any()))
                .thenReturn(new NotificationTemplatePreviewResult("[알림] 청년창업", "<p>청년창업</p>"));

        String body = "{\"templateId\":1,\"sampleVariables\":{\"policyName\":\"청년창업\"}}";
        mockMvc.perform(post("/api/v1/notification/admin/template/1/preview")
                        .with(jwtAuth(withAuthority("NOTIFICATION_TEMPLATE:READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("[알림] 청년창업"));
    }
}
