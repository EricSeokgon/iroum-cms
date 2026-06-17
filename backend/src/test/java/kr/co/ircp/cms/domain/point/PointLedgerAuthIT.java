package kr.co.ircp.cms.domain.point;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-POINTS-001 REQ-PNT-006 — 포인트 내역 권한 IT (실DB + MockMvc).
 *
 * <p>사용자는 본인 내역/총액만 조회 가능하며(me/* 엔드포인트는 SecurityContext userId 기반),
 * 관리자 ledger 엔드포인트는 POINTS:READ 권한이 필요하다(미보유 시 403).
 */
// @MX:NOTE: [AUTO] PointLedgerAuthIT — REQ-PNT-006 본인 데이터 격리 + 관리자 권한 게이트 검증.
@AutoConfigureMockMvc
@DisplayName("포인트 내역 권한 IT (SPEC-CMS-POINTS-001 REQ-PNT-006)")
class PointLedgerAuthIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer pt-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private String suffix;
    private long userA;
    private long userB;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userA = insertUser("pt-a-" + suffix);
        userB = insertUser("pt-b-" + suffix);
        // userA에게 10점, userB에게 99점 적립 내역 시드
        seedLedger(userA, "POST_CREATED", 10);
        seedSummary(userA, 10);
        seedLedger(userB, "POST_CREATED", 99);
        seedSummary(userB, 99);
    }

    @Test
    @DisplayName("REQ-PNT-006: 사용자 본인 요약 조회 — 본인 총액만 반환")
    void mySummary_returnsOwnTotalOnly() throws Exception {
        givenUserToken(userA, Set.of("USER"), Set.of());

        mockMvc.perform(get("/api/v1/users/me/points/summary")
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userA))
                .andExpect(jsonPath("$.totalPoints").value(10));
    }

    @Test
    @DisplayName("REQ-PNT-006: 사용자 본인 내역 조회 — 본인 내역만 반환(타인 내역 제외)")
    void myHistory_returnsOwnLedgerOnly() throws Exception {
        givenUserToken(userA, Set.of("USER"), Set.of());

        mockMvc.perform(get("/api/v1/users/me/points/history")
                        .header("Authorization", TOKEN)
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(userA));
    }

    @Test
    @DisplayName("REQ-PNT-006: POINTS:READ 미보유 사용자가 관리자 ledger 조회 → 403")
    void adminLedger_withoutPermission_forbidden() throws Exception {
        givenUserToken(userA, Set.of("USER"), Set.of());

        mockMvc.perform(get("/api/v1/admin/points/ledger")
                        .header("Authorization", TOKEN)
                        .param("userId", String.valueOf(userB)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("REQ-PNT-006: POINTS:READ 보유 관리자는 임의 사용자 ledger 조회 가능 → 200")
    void adminLedger_withPermission_returnsOk() throws Exception {
        givenUserToken(userA, Set.of("ADMIN"), Set.of("POINTS:READ"));

        mockMvc.perform(get("/api/v1/admin/points/ledger")
                        .header("Authorization", TOKEN)
                        .param("userId", String.valueOf(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(userB))
                .andExpect(jsonPath("$.content[0].points").value(99));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void givenUserToken(long id, Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "pt-user-" + id, roles, permissions,
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '포인트테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private void seedLedger(long userId, String eventType, int points) {
        jdbcTemplate.update(
                "INSERT INTO user_point_ledger (user_id, event_type, reference_id, points) " +
                "VALUES (?, ?, NULL, ?)", userId, eventType, points);
    }

    private void seedSummary(long userId, int total) {
        jdbcTemplate.update(
                "INSERT INTO user_point_summary (user_id, total_points, updated_at) " +
                "VALUES (?, ?, NOW()) ON CONFLICT (user_id) DO UPDATE SET total_points = EXCLUDED.total_points",
                userId, total);
    }
}
