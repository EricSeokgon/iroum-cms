package kr.co.ircp.cms.domain.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutRequest;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutResponse;
import kr.co.ircp.cms.domain.dashboard.service.DashboardLayoutService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DashboardLayoutController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-008 REQ-DASHBOARD-002 (REQ-VIZ-002): 레이아웃 CRUD + 클론 + 기본 지정 HTTP 계층 검증.
 */
@WebMvcTest(DashboardLayoutController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("DashboardLayoutController GREEN 테스트 (REQ-DASHBOARD-002)")
class DashboardLayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DashboardLayoutService service;

    private static LayoutResponse sample(Long id, String name, boolean isDefault) {
        return new LayoutResponse(
                id, 1L, name, "설명", isDefault,
                "{\"cols\":12}", List.of("DEPT_ADMIN"),
                List.of(),
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /dashboard/layouts — 사용자 레이아웃 목록 200 OK")
    void list_returnsOkWithLayouts() throws Exception {
        when(service.listForUser(any(), anyList())).thenReturn(List.of(
                sample(1L, "내 레이아웃", true),
                sample(2L, "공유 레이아웃", false)
        ));

        mockMvc.perform(get("/api/v1/dashboard/layouts")
                        .param("roles", "DEPT_ADMIN")
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("내 레이아웃"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @DisplayName("GET /dashboard/layouts/{id} — 단건 조회 200 OK")
    void get_returnsOkWithLayout() throws Exception {
        // 운영 컨트롤러는 service.getByIdForUser(id, resolveRoles()) 를 호출하므로
        // 동일 메소드를 stubbing 해야 한다.
        when(service.getByIdForUser(eq(7L), anyList()))
                .thenReturn(sample(7L, "테스트 레이아웃", false));

        mockMvc.perform(get("/api/v1/dashboard/layouts/7")
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("테스트 레이아웃"));
    }

    @Test
    @DisplayName("POST /dashboard/layouts — 레이아웃 생성 200 OK + body")
    void create_returnsOkWithLayout() throws Exception {
        LayoutRequest req = new LayoutRequest("새 레이아웃", "설명", "{\"cols\":12}",
                List.of("DEPT_ADMIN"), List.of());
        when(service.create(any(), any(LayoutRequest.class)))
                .thenReturn(sample(10L, "새 레이아웃", false));

        mockMvc.perform(post("/api/v1/dashboard/layouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:WRITE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("새 레이아웃"));
    }

    @Test
    @DisplayName("POST /dashboard/layouts — name 필수 누락 시 400 Bad Request")
    void create_missingName_returns400() throws Exception {
        // name(@NotBlank) 누락
        String invalidJson = "{\"description\":\"설명\"}";

        mockMvc.perform(post("/api/v1/dashboard/layouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:WRITE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /dashboard/layouts/{id} — 레이아웃 수정 200 OK")
    void update_returnsOkWithUpdatedLayout() throws Exception {
        LayoutRequest req = new LayoutRequest("수정된 이름", "수정 설명", null, null, null);
        when(service.update(eq(5L), any(), any(LayoutRequest.class)))
                .thenReturn(sample(5L, "수정된 이름", false));

        mockMvc.perform(put("/api/v1/dashboard/layouts/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:WRITE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("수정된 이름"));
    }

    @Test
    @DisplayName("DELETE /dashboard/layouts/{id} — 레이아웃 삭제 204 No Content")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/layouts/3")
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:WRITE"))))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(3L), any());
    }

    @Test
    @DisplayName("POST /dashboard/layouts/{id}/clone — 레이아웃 deep-copy 200 OK")
    void clone_returnsOkWithClonedLayout() throws Exception {
        when(service.clone(eq(7L), any())).thenReturn(sample(99L, "복제본", false));

        mockMvc.perform(post("/api/v1/dashboard/layouts/7/clone")
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:WRITE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.name").value("복제본"));
    }

    @Test
    @DisplayName("PUT /dashboard/layouts/{id}/default — 기본 레이아웃 지정 204 No Content")
    void setDefault_returnsNoContent() throws Exception {
        mockMvc.perform(put("/api/v1/dashboard/layouts/4/default")
                        .with(jwtAuth(withAuthority("DASHBOARD:LAYOUT:WRITE"))))
                .andExpect(status().isNoContent());

        verify(service).setDefault(eq(4L), any());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // DashboardLayoutController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(.anyRequest().authenticated())로
    // /api/v1/dashboard/** 경로 인증만 강제된다. 권한(role/authority)별 차등 통제는 없다.
    // (테스트 메서드의 @WithMockUser authorities = "DASHBOARD:LAYOUT:*"는 GREEN 단계
    // 사용자 식별을 위한 인증 컨텍스트 제공 목적이며, 운영 권한 정책 검증과 무관하다.)
    //
    // 본 슬라이스 테스트는 SecurityAutoConfiguration을 제외하므로 HTTP 레벨 정책이 미적용되며,
    // 메소드 레벨 정책 거부 트리거가 없어 ExceptionTranslationFilter가 EntryPoint를 호출하지 않는다.
    // 따라서 슬라이스에서 401(미인증) / 403(권한 부족) 응답을 결정적으로 검증할 수 없다.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────
}
