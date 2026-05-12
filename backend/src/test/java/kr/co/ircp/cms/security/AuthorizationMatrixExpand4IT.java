package kr.co.ircp.cms.security;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004 RUN Step 2 — HTTP 권한 매트릭스 IT 확장 4차 (최종, AUTHZ 트랙 종결).
 *
 * <p>본 IT는 AUTHZ-IT-EXPAND-001/002/003에서 커버하지 못한 운영 ~27 endpoint를 추가하여,
 * AuthorizationCoverageArchTest baseline 88 → 115 endpoint 갱신 + ArchUnit baseline
 * 100% IT 매핑 + OWASP A01 완전 검출 능력 도달 + AUTHZ 트랙 종결.
 *
 * <p>패턴: AUTHZ-MATRIX-001 + EXPAND-001/002/003 + REGRESSION-001 인프라 100% 재사용.
 *
 * <h3>본 SPEC v0.2 Step 1 인벤토리 결과 — 27 미커버 endpoint</h3>
 *
 * <table border="1" summary="EXPAND-004 미커버 분류">
 *   <thead>
 *     <tr><th>도메인</th><th>Controller</th><th>미커버 수</th><th>우선순위</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td rowspan="5">§A.1 Board (17)</td>
 *         <td>QnaController</td><td>5</td><td>高</td></tr>
 *     <tr><td>SurveyController</td><td>4</td><td>高</td></tr>
 *     <tr><td>FaqController</td><td>4</td><td>高</td></tr>
 *     <tr><td>PublicationController</td><td>3</td><td>中</td></tr>
 *     <tr><td>BbsMasterController</td><td>1</td><td>低</td></tr>
 *     <tr><td rowspan="4">§A.2 Content (9)</td>
 *         <td>ContentBlockController</td><td>3</td><td>中</td></tr>
 *     <tr><td>PopupController</td><td>3</td><td>中</td></tr>
 *     <tr><td>PageController</td><td>2</td><td>低</td></tr>
 *     <tr><td>TemplateController</td><td>1</td><td>低</td></tr>
 *     <tr><td rowspan="3">§A.3 AuthSystem (3)</td>
 *         <td>RoleController</td><td>1</td><td>低</td></tr>
 *     <tr><td>UserController (잔여)</td><td>1</td><td>低</td></tr>
 *     <tr><td>CacheAdminController</td><td>1</td><td>低</td></tr>
 *   </tbody>
 * </table>
 *
 * <p><b>합계</b>: 27 endpoint × 3 시나리오 ≈ 78 AC (Step 3 활성화)
 *
 * <h3>본 SPEC 완성 시 OWASP A01 회귀 검출 7중 검증</h3>
 * <ul>
 *   <li>HTTP 1차 (AUTHZ-MATRIX-001): 19 AC, 6 endpoint</li>
 *   <li>HTTP 확장 1차 (AUTHZ-IT-EXPAND-001): 88 AC, 29 endpoint</li>
 *   <li>HTTP 확장 2차 (AUTHZ-IT-EXPAND-002): 57 AC, 19 endpoint</li>
 *   <li>HTTP 확장 3차 (AUTHZ-IT-EXPAND-003): 106 AC, 35 endpoint</li>
 *   <li>HTTP 확장 4차 (AUTHZ-IT-EXPAND-004 — 본 SPEC): ~78 AC, ~27 endpoint</li>
 *   <li>메소드 슬라이스 (CTRL-AUTHZ-COVERAGE-001): 31 AC</li>
 *   <li>ArchUnit 자동 검출 (AUTHZ-AUTODETECT-001): 4 AC, 115 endpoint baseline (100%)</li>
 * </ul>
 *
 * <p>합계: <b>~383 AC</b> + ArchUnit baseline 100% IT 매핑 달성 + AUTHZ 트랙 종결.
 *
 * <h3>Step 2 — 인프라 신설 + smoke test만 활성화</h3>
 *
 * <p>본 RUN Step 2에서는 IT 클래스 부팅 + JWT Mock 주입 검증의 smoke test 1건만 활성화한다.
 * ~27 endpoint × 3 시나리오는 Step 3 (Phase A-C 단계 분할 또는 일괄)에서 활성화한다.
 *
 * <h3>패턴 재사용</h3>
 * <ul>
 *   <li>{@link AuthorizationMatrixExpand2IT}/{@link AuthorizationMatrixExpand3IT}의 helper 패턴 100% 재사용</li>
 *   <li>REGRESSION-001 v0.5/v0.6/v0.8에서 검증된 응답 코드 분기 (AUTH_REQUIRED 401 / ACCESS_DENIED 403)</li>
 *   <li>DTO 정상 body 정상화 (각 endpoint required fields 충족)</li>
 *   <li>assertAuthzPassed helper — 도메인 RuntimeException ServletException wrap 허용</li>
 *   <li>META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 충족</li>
 * </ul>
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004 (REQ-AM-EXP4-001/002/003/004/005)
 */
// @MX:NOTE: [AUTO] AuthorizationMatrixExpand4IT — 잔여 ~27 endpoint IT 매트릭스 (확장 4차, 최종)
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("HTTP 권한 매트릭스 IT 확장 4차 (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-004, AUTHZ 트랙 최종)")
class AuthorizationMatrixExpand4IT {

    // ─── Testcontainers PostgreSQL 16 (AUTHZ-MATRIX/EXPAND-001/002/003 패턴 일관) ─────
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iroum_cms_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void overrideDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // PII 더미 키 (32 bytes base64) — SPEC-PII-001 인프라 일관
        registry.add("pii.keyvault.keys.v1", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        registry.add("pii.keyvault.hmac-key", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    @SuppressWarnings("unused") // Step 3에서 활성화될 시나리오에서 사용 예정
    private static final String VALID_TOKEN = "valid.jwt.token";

    // ─── JWT Mock helper (AUTHZ-IT-EXPAND-001/002/003 패턴 100% 재사용) ─────────────
    @SuppressWarnings("unused") // Step 3에서 활성화될 시나리오에서 사용 예정
    private void givenValidToken(Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "testuser", roles, permissions, Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    /**
     * 권한 통과 검증 helper — 401/403 아님 + service domain exception 허용.
     *
     * <p>REGRESSION-001 v0.5에서 검증된 패턴. 운영 GlobalExceptionHandler가 IllegalArgumentException/
     * 도메인 RuntimeException을 처리하지 않아 ServletException으로 wrap되는 경우를 권한 통과 IT 본질적
     * PASS로 처리. AccessDeniedException/AuthenticationException은 권한 실패이므로 제외.
     */
    @SuppressWarnings("unused")
    private void assertAuthzPassed(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        try {
            mockMvc.perform(request)
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        } catch (jakarta.servlet.ServletException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return;
            }
            if (cause instanceof RuntimeException
                    && !(cause instanceof org.springframework.security.access.AccessDeniedException)
                    && !(cause instanceof org.springframework.security.core.AuthenticationException)) {
                return;
            }
            throw e;
        }
    }

    // =================================================================================
    // §0 인프라 smoke test — Step 2에서 유일하게 활성화되는 시나리오
    // =================================================================================

    @Test
    @DisplayName("§0 AC-AME4-002-1: 컨텍스트 부팅 + JwtTokenProvider/TokenBlacklistMapper Mock 주입 + JwtTestAuth helper 동작")
    void contextLoadsAndJwtAuthMockable() {
        assertNotNull(mockMvc, "MockMvc 주입 확인 (운영 SecurityFilterChain 적재 결과)");
        assertNotNull(jwtTokenProvider, "JwtTokenProvider @MockitoBean 주입 확인");
        assertNotNull(tokenBlacklistMapper, "TokenBlacklistMapper @MockitoBean 주입 확인");
        givenValidToken(Set.of(), Set.of());
    }

    // =================================================================================
    // §A REQ-AM-EXP4-001 — 27 미커버 endpoint 매트릭스 (~78 AC, Step 3 활성화)
    // =================================================================================

    /** §A.1 BoardDomainTests — Bbs DELETE 1 + Publication 3 = 4 endpoint (Phase A 부분 활성화). */
    @Nested
    @DisplayName("§A.1 BoardDomainTests (Bbs 1 + Publication 3 = 4 미커버, Step 3 Phase A 활성화)")
    class BoardDomainTests {

        // ── DELETE /api/v1/boards/{id} — hasRole(ADMIN) ──
        @Test
        @DisplayName("AC-AME4-A1-1: DELETE /api/v1/boards/{id} — Authorization 부재 + 401")
        void bbsDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/boards/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-2: DELETE /api/v1/boards/{id} — USER 역할 + 403")
        void bbsDelete_missingAdminRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(delete("/api/v1/boards/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-3: DELETE /api/v1/boards/{id} — ADMIN 보유 + 401/403 아님")
        void bbsDelete_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/boards/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PublicationController (CONTENT:WRITE OR ADMIN/SUPER_ADMIN/CONTENT_ADMIN) ──
        // PublicationCreateRequest required: title, publicationYear, documentType
        private static final String PUB_CREATE_BODY =
                "{\"title\":\"테스트 발간물\",\"publicationYear\":2026,\"documentType\":\"REPORT\"}";
        private static final String PUB_UPDATE_BODY = "{}";

        // ── POST /api/v1/publications ──
        @Test
        @DisplayName("AC-AME4-A1-4: POST /api/v1/publications — Authorization 부재 + 401")
        void pubCreate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/publications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PUB_CREATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-5: POST /api/v1/publications — CONTENT:WRITE/ADMIN/CONTENT_ADMIN 모두 부재 + 403")
        void pubCreate_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(post("/api/v1/publications")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PUB_CREATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-6: POST /api/v1/publications — CONTENT_ADMIN 보유 + 401/403 아님 (OR bypass)")
        void pubCreate_hasContentAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("CONTENT_ADMIN"), Set.of());
            assertAuthzPassed(post("/api/v1/publications")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PUB_CREATE_BODY));
        }

        // ── PUT /api/v1/publications/{id} ──
        @Test
        @DisplayName("AC-AME4-A1-7: PUT /api/v1/publications/{id} — Authorization 부재 + 401")
        void pubUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/publications/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PUB_UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-8: PUT /api/v1/publications/{id} — 모든 권한 부재 + 403")
        void pubUpdate_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(put("/api/v1/publications/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PUB_UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-9: PUT /api/v1/publications/{id} — CONTENT:WRITE 보유 + 401/403 아님 (OR bypass)")
        void pubUpdate_hasContentWriteAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));
            assertAuthzPassed(put("/api/v1/publications/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PUB_UPDATE_BODY));
        }

        // ── DELETE /api/v1/publications/{id} ──
        @Test
        @DisplayName("AC-AME4-A1-10: DELETE /api/v1/publications/{id} — Authorization 부재 + 401")
        void pubDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/publications/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-11: DELETE /api/v1/publications/{id} — 모든 권한 부재 + 403")
        void pubDelete_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(delete("/api/v1/publications/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-12: DELETE /api/v1/publications/{id} — ADMIN 보유 + 401/403 아님 (OR bypass)")
        void pubDelete_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/publications/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── FaqController (CONTENT:WRITE OR ADMIN/SUPER_ADMIN/CONTENT_ADMIN) ──
        // FaqCreateRequest required: categoryCode, question, answerHtml, sortOrder
        private static final String FAQ_CREATE_BODY =
                "{\"categoryCode\":\"GEN\",\"question\":\"테스트 질문\",\"answerHtml\":\"<p>답변</p>\",\"sortOrder\":0}";
        private static final String FAQ_UPDATE_BODY = "{}";
        private static final String FAQ_REORDER_BODY = "{\"items\":[{\"id\":1,\"sortOrder\":0}]}";

        // ── POST /api/v1/faqs ──
        @Test
        @DisplayName("AC-AME4-A1-13: POST /api/v1/faqs — Authorization 부재 + 401")
        void faqCreate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/faqs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FAQ_CREATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-14: POST /api/v1/faqs — 모든 권한 부재 + 403")
        void faqCreate_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(post("/api/v1/faqs")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FAQ_CREATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-15: POST /api/v1/faqs — CONTENT_ADMIN 보유 + 401/403 아님 (OR bypass)")
        void faqCreate_hasContentAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("CONTENT_ADMIN"), Set.of());
            assertAuthzPassed(post("/api/v1/faqs")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(FAQ_CREATE_BODY));
        }

        // ── PUT /api/v1/faqs/reorder ──
        @Test
        @DisplayName("AC-AME4-A1-16: PUT /api/v1/faqs/reorder — Authorization 부재 + 401")
        void faqReorder_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/faqs/reorder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FAQ_REORDER_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-17: PUT /api/v1/faqs/reorder — 모든 권한 부재 + 403")
        void faqReorder_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(put("/api/v1/faqs/reorder")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FAQ_REORDER_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-18: PUT /api/v1/faqs/reorder — CONTENT:WRITE 보유 + 401/403 아님")
        void faqReorder_hasContentWrite_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));
            assertAuthzPassed(put("/api/v1/faqs/reorder")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(FAQ_REORDER_BODY));
        }

        // ── PUT /api/v1/faqs/{id} ──
        @Test
        @DisplayName("AC-AME4-A1-19: PUT /api/v1/faqs/{id} — Authorization 부재 + 401")
        void faqUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/faqs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FAQ_UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-20: PUT /api/v1/faqs/{id} — 모든 권한 부재 + 403")
        void faqUpdate_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(put("/api/v1/faqs/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(FAQ_UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-21: PUT /api/v1/faqs/{id} — ADMIN 보유 + 401/403 아님")
        void faqUpdate_hasAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            assertAuthzPassed(put("/api/v1/faqs/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(FAQ_UPDATE_BODY));
        }

        // ── DELETE /api/v1/faqs/{id} ──
        @Test
        @DisplayName("AC-AME4-A1-22: DELETE /api/v1/faqs/{id} — Authorization 부재 + 401")
        void faqDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/faqs/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME4-A1-23: DELETE /api/v1/faqs/{id} — 모든 권한 부재 + 403")
        void faqDelete_missingAllRoles_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(delete("/api/v1/faqs/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME4-A1-24: DELETE /api/v1/faqs/{id} — SUPER_ADMIN 보유 + 401/403 아님 (OR bypass)")
        void faqDelete_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/faqs/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §A.2 ContentDomainTests — Block 3 + Popup 3 + Page 2 + Template DELETE 1 = 9 endpoint (Phase B). */
    @Nested
    @DisplayName("§A.2 ContentDomainTests (9 미커버, Step 3 Phase B 활성화)")
    class ContentDomainTests {
        @Test
        @Disabled("Step 3 (Phase B)에서 활성화 예정 — Block/Popup/Page/Template 잔여 endpoint")
        @DisplayName("§A.2 placeholder: Step 3 활성화 대기")
        void contentDomain_placeholder_step3() {
            // Block: PUT order/DELETE/get
            // Popup: list/PUT/DELETE
            // Page: list/get
            // Template: DELETE
        }
    }

    /** §A.3 AuthSystemDomainTests — Role 1 + User 1 + CacheAdmin 1 = 3 endpoint (Phase C). */
    @Nested
    @DisplayName("§A.3 AuthSystemDomainTests (3 미커버, Step 3 Phase C 활성화)")
    class AuthSystemDomainTests {
        @Test
        @Disabled("Step 3 (Phase C)에서 활성화 예정 — Role/User 추가/CacheAdmin 잔여")
        @DisplayName("§A.3 placeholder: Step 3 활성화 대기")
        void authSystemDomain_placeholder_step3() {
            // Role: GET list
            // User: 추가 endpoint
            // CacheAdmin: PUT
        }
    }
}
