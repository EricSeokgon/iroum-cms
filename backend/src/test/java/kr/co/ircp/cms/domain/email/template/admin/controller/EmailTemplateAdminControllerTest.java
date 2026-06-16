package kr.co.ircp.cms.domain.email.template.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.PagedResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.exception.DuplicateEmailTemplateException;
import kr.co.ircp.cms.domain.email.template.admin.exception.EmailTemplateNotFoundException;
import kr.co.ircp.cms.domain.email.template.admin.exception.MissingTemplateVariableException;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateSendLogService;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EmailTemplateAdminController GREEN 테스트 (T4).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 — HTTP 계층 응답 구조 및 예외→상태코드 매핑 검증.
 * 보안(@PreAuthorize)은 SecurityAutoConfiguration 제외로 본 테스트 범위 밖(IT 매트릭스에서 검증).
 */
@WebMvcTest(EmailTemplateAdminController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("EmailTemplateAdminController GREEN 테스트 (REQ-ET-001~005/020)")
class EmailTemplateAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailTemplateService templateService;

    @MockitoBean
    private EmailTemplateSendLogService sendLogService;

    private static EmailTemplateResponse sample() {
        return new EmailTemplateResponse(1L, "OTP", "OTP 메일", "OTP", "ko",
                "제목", "<p>본문</p>", "평문", List.of(), true, 1L, 1L, null, null);
    }

    @Test
    @DisplayName("GET 목록 — 200 + 페이지 구조")
    void list_returnsPagedResponse() throws Exception {
        when(templateService.list(any()))
                .thenReturn(new PagedResponse<>(List.of(sample()), 0, 20, 1L));

        mockMvc.perform(get("/api/v1/admin/email-templates")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.content[0].code").value("OTP"));
    }

    @Test
    @DisplayName("GET 상세 — 200")
    void detail_returnsTemplate() throws Exception {
        when(templateService.get(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/v1/admin/email-templates/1")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET 상세 미존재 — 404")
    void detail_notFound() throws Exception {
        when(templateService.get(99L))
                .thenThrow(new EmailTemplateNotFoundException("없음"));

        mockMvc.perform(get("/api/v1/admin/email-templates/99")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST 생성 — 201")
    void create_returns201() throws Exception {
        when(templateService.create(any(), any())).thenReturn(sample());
        var body = Map.of("code", "OTP", "name", "OTP 메일", "templateType", "OTP",
                "subject", "제목", "bodyHtml", "<p>본문</p>");

        mockMvc.perform(post("/api/v1/admin/email-templates")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OTP"));
    }

    @Test
    @DisplayName("POST 생성 중복 — 409")
    void create_duplicate409() throws Exception {
        when(templateService.create(any(), any()))
                .thenThrow(new DuplicateEmailTemplateException("중복"));
        var body = Map.of("code", "OTP", "name", "n", "templateType", "OTP",
                "subject", "s", "bodyHtml", "<p>b</p>");

        mockMvc.perform(post("/api/v1/admin/email-templates")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST 미리보기 — 200 렌더링 결과")
    void preview_returnsRenderResult() throws Exception {
        when(templateService.preview(anyLong(), any()))
                .thenReturn(new RenderResult("치환된 제목", "<p>치환됨</p>", null));

        mockMvc.perform(post("/api/v1/admin/email-templates/1/preview")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"name\":\"홍길동\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("치환된 제목"));
    }

    @Test
    @DisplayName("POST 미리보기 필수변수 누락 — 400 + 누락 목록")
    void preview_missingVariable400() throws Exception {
        doThrow(new MissingTemplateVariableException(List.of("code")))
                .when(templateService).preview(anyLong(), any());

        mockMvc.perform(post("/api/v1/admin/email-templates/1/preview")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.missingVariables[0]").value("code"));
    }

    @Test
    @DisplayName("DELETE — 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/email-templates/1")
                        .with(jwtAuth(withAuthority("EMAIL_TEMPLATE:DELETE"))))
                .andExpect(status().isNoContent());
    }
}
