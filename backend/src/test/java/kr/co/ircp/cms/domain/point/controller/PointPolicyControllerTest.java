package kr.co.ircp.cms.domain.point.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.point.dto.PointPolicyDto;
import kr.co.ircp.cms.domain.point.dto.PointPolicyUpdateRequest;
import kr.co.ircp.cms.domain.point.service.PointPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-POINTS-001 — PointPolicyController @WebMvcTest (REQ-PNT-005).
 *
 * <p>GET=POINTS:READ, PUT=POINTS:WRITE 권한 게이트 + 검증(400) 검증.
 */
@WebMvcTest(PointPolicyController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PointPolicyController GREEN 테스트 (SPEC-CMS-POINTS-001)")
class PointPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PointPolicyService pointPolicyService;

    private static final JwtPrincipal READER =
            new JwtPrincipal(1L, "reader", Set.of("ADMIN"), Set.of("POINTS:READ"));
    private static final JwtPrincipal WRITER =
            new JwtPrincipal(2L, "writer", Set.of("ADMIN"), Set.of("POINTS:READ", "POINTS:WRITE"));
    private static final JwtPrincipal NORMAL =
            new JwtPrincipal(3L, "user", Set.of("USER"), Set.of());

    @Test
    @DisplayName("GET /policy — POINTS:READ: 200 + 정책")
    void getPolicy_withRead_returnsOk() throws Exception {
        when(pointPolicyService.getPolicy()).thenReturn(new PointPolicyDto(true, 10, 5, 2));

        mockMvc.perform(get("/api/v1/admin/points/policy")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(READER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.postPoints").value(10));
    }

    @Test
    @DisplayName("GET /policy — 권한 없는 USER: 403")
    void getPolicy_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/points/policy")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(NORMAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /policy — POINTS:WRITE: 200 + 갱신 정책")
    void updatePolicy_withWrite_returnsOk() throws Exception {
        when(pointPolicyService.updatePolicy(any())).thenReturn(new PointPolicyDto(true, 20, 10, 4));
        PointPolicyUpdateRequest req = new PointPolicyUpdateRequest(true, 20, 10, 4);

        mockMvc.perform(put("/api/v1/admin/points/policy")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(WRITER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postPoints").value(20));
    }

    @Test
    @DisplayName("PUT /policy — POINTS:READ 만 보유: 403 (WRITE 필요)")
    void updatePolicy_withReadOnly_forbidden() throws Exception {
        PointPolicyUpdateRequest req = new PointPolicyUpdateRequest(true, 20, 10, 4);

        mockMvc.perform(put("/api/v1/admin/points/policy")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(READER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /policy — 음수 포인트: 400 (Bean Validation)")
    void updatePolicy_negativePoints_returns400() throws Exception {
        PointPolicyUpdateRequest req = new PointPolicyUpdateRequest(true, -1, 10, 4);

        mockMvc.perform(put("/api/v1/admin/points/policy")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth(WRITER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    /** roles + permissions를 모두 GrantedAuthority로 반영(JwtPrincipal.getAuthorities() 활용). */
    private Authentication auth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }
}
