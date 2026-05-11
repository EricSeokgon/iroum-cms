package kr.co.ircp.cms.domain.safety.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.safety.dto.ChecklistItemRequest;
import kr.co.ircp.cms.domain.safety.dto.ChecklistItemResponse;
import kr.co.ircp.cms.domain.safety.dto.PreviewRequest;
import kr.co.ircp.cms.domain.safety.dto.PreviewResponse;
import kr.co.ircp.cms.domain.safety.dto.TemplateRequest;
import kr.co.ircp.cms.domain.safety.dto.TemplateResponse;
import kr.co.ircp.cms.domain.safety.exception.SafetyTemplateNotFoundException;
import kr.co.ircp.cms.domain.safety.service.SafetyTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SafetyTemplateController @WebMvcTest (GREEN 단계).
 *
 * <p>REQ-SAFETY-005 — 가이드라인 템플릿 관리 HTTP 계층 검증.
 */
@WebMvcTest(SafetyTemplateController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SafetyTemplateController GREEN 테스트 (REQ-SAFETY-005)")
class SafetyTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SafetyTemplateService templateService;

    private static final Long ADMIN_USER_ID = 1L;

    private static TemplateResponse sampleTemplate(Long id, String code, String version) {
        return new TemplateResponse(
                id, code, "건설 안전 가이드", "건설업 안전 가이드 템플릿",
                List.of("C20", "C21"), List.of("HIGH", "MEDIUM"),
                "{\"sections\":[]}", "PUBLISHED", version, "APPROVED", Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/templates — 템플릿 목록 200 OK")
    void list_returnsOk() throws Exception {
        when(templateService.listTemplates()).thenReturn(List.of(
                sampleTemplate(1L, "T_CONST_HIGH", "v1.0"),
                sampleTemplate(2L, "T_MFR_MID", "v1.0")
        ));

        mockMvc.perform(get("/api/v1/safety/admin/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("T_CONST_HIGH"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/templates/{id} — 단건 조회 200 OK")
    void get_returnsOk() throws Exception {
        when(templateService.getTemplate(eq(1L))).thenReturn(sampleTemplate(1L, "T_CONST_HIGH", "v1.0"));

        mockMvc.perform(get("/api/v1/safety/admin/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("T_CONST_HIGH"))
                .andExpect(jsonPath("$.version").value("v1.0"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/templates/{id} — 미존재 시 404")
    void get_notFound_returns404() throws Exception {
        when(templateService.getTemplate(eq(999L)))
                .thenThrow(new SafetyTemplateNotFoundException(999L));

        mockMvc.perform(get("/api/v1/safety/admin/templates/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/templates — 신규 템플릿 201 Created")
    void create_returnsCreated() throws Exception {
        TemplateRequest request = new TemplateRequest(
                "T_NEW", "신규 템플릿", "설명",
                List.of("C20"), List.of("HIGH"),
                "{\"sections\":[]}", "DRAFT"
        );
        TemplateResponse created = sampleTemplate(50L, "T_NEW", "v1.0");
        when(templateService.createTemplate(any(TemplateRequest.class), anyLong())).thenReturn(created);

        mockMvc.perform(post("/api/v1/safety/admin/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuth(ADMIN_USER_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.code").value("T_NEW"));
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/templates — 필수 필드 누락 시 400")
    void create_missingRequired_returns400() throws Exception {
        // code/name (@NotBlank) 누락
        String invalidJson = "{\"description\":\"설명만\"}";

        mockMvc.perform(post("/api/v1/safety/admin/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuth(ADMIN_USER_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/safety/admin/templates/{id} — 신규 버전 발행 200 OK")
    void releaseNewVersion_returnsOk() throws Exception {
        TemplateRequest request = new TemplateRequest(
                "T_CONST_HIGH", "건설 안전 가이드 v2", "v2 설명",
                List.of("C20"), List.of("HIGH"),
                "{\"sections\":[\"new\"]}", "APPROVED"
        );
        TemplateResponse updated = sampleTemplate(1L, "T_CONST_HIGH", "v1.1");
        when(templateService.releaseNewVersion(eq(1L), any(TemplateRequest.class), anyLong()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/safety/admin/templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(adminAuth(ADMIN_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.version").value("v1.1"));
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/templates/{id}/preview — 미리보기 200 OK")
    void preview_returnsOk() throws Exception {
        PreviewRequest request = new PreviewRequest("HIGH", "C20", "샘플 회사");
        when(templateService.previewTemplate(eq(1L), any(PreviewRequest.class)))
                .thenReturn(new PreviewResponse("<html>preview</html>"));

        mockMvc.perform(post("/api/v1/safety/admin/templates/1/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHtml").value("<html>preview</html>"));
    }

    @Test
    @DisplayName("DELETE /api/v1/safety/admin/templates/{id} — 아카이브 204 No Content")
    void archive_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/safety/admin/templates/3"))
                .andExpect(status().isNoContent());

        verify(templateService).archiveTemplate(3L);
    }

    @Test
    @DisplayName("DELETE /api/v1/safety/admin/templates/{id} — 미존재 시 404")
    void archive_notFound_returns404() throws Exception {
        doThrow(new SafetyTemplateNotFoundException(404L))
                .when(templateService).archiveTemplate(404L);

        mockMvc.perform(delete("/api/v1/safety/admin/templates/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/templates/{id}/checklist — 체크리스트 항목 200 OK")
    void listChecklist_returnsOk() throws Exception {
        ChecklistItemResponse item = new ChecklistItemResponse(
                1L, 10L, "PPE", "안전모 착용", "HIGH", 1, "ACTIVE"
        );
        when(templateService.listChecklistItems(eq(10L))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/safety/admin/templates/10/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].itemText").value("안전모 착용"));
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/templates/{id}/checklist — 체크리스트 항목 추가 200 OK")
    void addChecklist_returnsOk() throws Exception {
        ChecklistItemRequest request = new ChecklistItemRequest(
                "PPE", "안전화 착용", "HIGH", 2
        );
        ChecklistItemResponse response = new ChecklistItemResponse(
                2L, 10L, "PPE", "안전화 착용", "HIGH", 2, "ACTIVE"
        );
        when(templateService.addChecklistItem(eq(10L), any(ChecklistItemRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/safety/admin/templates/10/checklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.itemText").value("안전화 착용"));
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // SafetyTemplateController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(.anyRequest().authenticated())로
    // /api/v1/safety/admin/templates/** 경로 인증만 강제된다. 권한(role/authority)별 차등 통제는 없다.
    //
    // 또한 일부 endpoint는 @AuthenticationPrincipal Long createdBy 인자를 사용하므로
    // 익명 요청 시 SecurityContext가 비어 createdBy가 null로 주입되어
    // controller/service 본체에서 NullPointerException(500)이 발생한다 — 401/403 결정 검증 불가.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────

    // ─── 헬퍼: principal로 Long(adminUserId) 직접 사용 ───────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken adminAuth(
            Long adminUserId) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                adminUserId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }
}
