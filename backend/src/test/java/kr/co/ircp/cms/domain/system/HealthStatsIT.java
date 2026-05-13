package kr.co.ircp.cms.domain.system;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-005 §F (헬스체크) + §C (운영 대시보드) + §B (수동 재집계) IT.
 *
 * <p>커버리지:
 * <ul>
 *   <li>§F REQ-SYSTEM-006-D-1 — 통합 헬스체크 UP 확인 (1 AC)</li>
 *   <li>§C REQ-SYSTEM-003-D-1 — KPI 위젯 API (1 AC)</li>
 *   <li>§C REQ-SYSTEM-003-D-2 — 30일 추이 그래프 (1 AC)</li>
 *   <li>§C REQ-SYSTEM-003-D-3 — 인기 페이지 Top 10 (1 AC)</li>
 *   <li>§B REQ-SYSTEM-002-D-4 — 수동 재집계 (1 AC)</li>
 * </ul>
 *
 * <p>인증 모델:
 * <ul>
 *   <li>/actuator/health: permitAll (SecurityConfig 화이트리스트)</li>
 *   <li>/api/v1/system/dashboard/kpi: SYSTEM:DASHBOARD authority</li>
 *   <li>/api/v1/system/stats/**: SYSTEM:STATS authority</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] HealthStatsIT — SPEC-CMS-005 §F 헬스체크 + §C 운영 대시보드 IT
// @MX:NOTE: [AUTO] §F-2(DB down 503) 와 §C-4(캐시) 는 수동 검증 항목 — IT 대상 외
// @MX:SPEC: SPEC-CMS-005#REQ-SYSTEM-006-D
@AutoConfigureMockMvc
@DisplayName("헬스체크 + 운영 대시보드 IT (SPEC-CMS-005 §F + §C)")
class HealthStatsIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-stats-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;

    @BeforeEach
    void setUp() {
        // 통계/대시보드 권한을 가진 ADMIN 사용자 사전 등록
        adminId = insertUser("stats-admin-" + uid());
    }

    // ─── §F-1 REQ-SYSTEM-006-D-1: 통합 헬스체크 ─────────────────────────────

    @Nested
    @DisplayName("§F: 헬스체크 (REQ-SYSTEM-006-D)")
    class Health {

        @Test
        @DisplayName("REQ-SYSTEM-006-D-1 — GET /actuator/health (no auth) → 200 UP")
        // @MX:NOTE: [AUTO] /actuator/health 는 SecurityConfig 의 화이트리스트.
        // DB 컴포넌트 상세는 management.endpoint.health.show-details=when-authorized 로
        // 미인증 호출 시 components 가 노출되지 않을 수 있다 → status:UP 만 검증.
        void health_returns200_withStatusUp() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    // ─── §C-1 REQ-SYSTEM-003-D-1: KPI 위젯 ──────────────────────────────────

    @Nested
    @DisplayName("§C: 운영 대시보드 (REQ-SYSTEM-003-D)")
    class Dashboard {

        @Test
        @DisplayName("REQ-SYSTEM-003-D-1 — GET /dashboard/kpi (SYSTEM:DASHBOARD) → 200")
        // @MX:NOTE: [AUTO] KPI 응답 스키마는 DashboardKpiResponse 내부 필드.
        // IT 는 200 OK + JSON object 형태만 확인 (필드별 값은 단위 테스트 책임).
        void kpi_asAdmin_returns200() throws Exception {
            givenAdminToken();
            mockMvc.perform(get("/api/v1/system/dashboard/kpi")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isMap());
        }

        @Test
        @DisplayName("REQ-SYSTEM-003-D-2 — GET /stats/trend (SYSTEM:STATS) → 200 배열")
        void trend_asAdmin_returns200_asArray() throws Exception {
            givenAdminToken();
            mockMvc.perform(get("/api/v1/system/stats/trend")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("REQ-SYSTEM-003-D-3 — GET /stats/top-pages (SYSTEM:STATS) → 200 배열")
        void topPages_asAdmin_returns200_asArray() throws Exception {
            givenAdminToken();
            mockMvc.perform(get("/api/v1/system/stats/top-pages")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ─── §B-4 REQ-SYSTEM-002-D-4: 수동 재집계 ────────────────────────────────

    @Nested
    @DisplayName("§B: 통계 재집계 (REQ-SYSTEM-002-D)")
    class Recompute {

        @Test
        @DisplayName("REQ-SYSTEM-002-D-4 — POST /stats/recompute?from=&to= (SYSTEM:STATS) → 200")
        // @MX:NOTE: [AUTO] 재집계는 동기 호출이며 응답으로 {"message":"재집계 완료"} 를 반환한다.
        // 실제 집계 결과 검증은 service 레벨 단위 테스트로 위임.
        void recompute_asAdmin_returns200() throws Exception {
            givenAdminToken();
            mockMvc.perform(post("/api/v1/system/stats/recompute")
                            .header("Authorization", TOKEN)
                            .param("from", "2026-04-01")
                            .param("to", "2026-04-15"))
                    .andExpect(status().isOk());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void givenAdminToken() {
        // ADMIN 역할 + SYSTEM:DASHBOARD + SYSTEM:STATS 두 authority 모두 부여
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "stats-admin-" + adminId,
                Set.of("ADMIN"),
                Set.of("SYSTEM:DASHBOARD", "SYSTEM:STATS"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '통계관리자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }
}
