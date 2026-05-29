package kr.co.ircp.cms.domain.dashboard.preference;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.dashboard.preference.entity.UserDashboardPreference;
import kr.co.ircp.cms.domain.dashboard.preference.repository.UserDashboardPreferenceMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 환경설정 5개 엔드포인트 통합 테스트.
 *
 * <p>실제 PostgreSQL 16 + Flyway V39 + JwtAuthenticationFilter 적재 환경에서 다음을 검증한다.
 * <ul>
 *   <li>AC-DP-API-1 — GET lazy 생성 (row 없으면 DEFAULT 응답 + DB INSERT)</li>
 *   <li>AC-DP-API-2 — PATCH 부분 갱신 (theme 만 변경, density 보존)</li>
 *   <li>AC-DP-API-3 — PATCH 잘못된 enum → 400 Bad Request</li>
 *   <li>AC-DP-002-5 — POST /reset 스타일 초기화 + hidden 보존</li>
 *   <li>AC-DP-001-1/2/5 — PATCH /widgets/{layoutId}/hidden 토글</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] UserDashboardPreferenceIT — Preference 5/6 엔드포인트 IT (PostgreSQL Singleton Container)
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 §7 / acceptance.md AC-DP-API-1~3, AC-DP-001, AC-DP-002
@AutoConfigureMockMvc
@DisplayName("UserDashboardPreference 통합 테스트 (SPEC-CMS-DASHBOARD-PERSONALIZE-001)")
class UserDashboardPreferenceIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserDashboardPreferenceMapper prefMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private long userId;

    @BeforeEach
    void setUp() {
        userId = insertTestUser("pref-it-" + UUID.randomUUID());
    }

    private void givenValidToken(long uid, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                uid, "pref-it-user", roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    private long insertTestUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, " +
                        "password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '테스트사용자', 'ACTIVE', " +
                        "?, 1, NOW(), NOW(), NOW())",
                username,
                "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    // =================================================================================
    // §A 환경설정 GET — Lazy 생성
    // =================================================================================
    @Nested
    @DisplayName("§A GET /preference Lazy 생성")
    class GetLazy {

        @Test
        @DisplayName("AC-DP-API-1: row 없으면 DEFAULT 값으로 INSERT 후 200 OK 응답")
        void get_lazyCreatesRow_whenMissing() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));
            assertThat(prefMapper.findByUserId(userId)).as("초기 row 없음").isEmpty();

            mockMvc.perform(get("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("SYSTEM"))
                    .andExpect(jsonPath("$.density").value("NORMAL"))
                    .andExpect(jsonPath("$.font_scale").value(1.0))
                    .andExpect(jsonPath("$.color_palette_preference").value("DEFAULT"))
                    .andExpect(jsonPath("$.sidebar_collapsed").value(false))
                    .andExpect(jsonPath("$.user_id").value(userId));

            // lazy 생성 확인
            UserDashboardPreference saved = prefMapper.findByUserId(userId).orElseThrow();
            assertThat(saved.getTheme()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("AC-DP-API-1: 두 번째 GET 호출은 INSERT 없이 동일 row 반환 (idempotent)")
        void get_idempotent_secondCall() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));

            mockMvc.perform(get("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk());

            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_dashboard_preference WHERE user_id = ?",
                    Long.class, userId);
            assertThat(count).as("lazy 생성은 1회만 발생").isEqualTo(1L);
        }
    }

    // =================================================================================
    // §B PATCH /preference 부분 갱신
    // =================================================================================
    @Nested
    @DisplayName("§B PATCH /preference 부분 갱신")
    class PatchPartial {

        @Test
        @DisplayName("AC-DP-API-2: theme 만 전송 시 density 는 보존, theme 만 갱신")
        void patch_partialUpdate_preservesOtherFields() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));
            // 사전 GET → lazy 생성
            mockMvc.perform(get("/api/v1/dashboard/preference")
                    .header("Authorization", "Bearer " + VALID_TOKEN)).andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"theme\":\"DARK\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("DARK"))
                    .andExpect(jsonPath("$.density").value("NORMAL")); // 보존
        }

        @Test
        @DisplayName("AC-DP-API-3: 잘못된 enum (theme=PINK) → 400 Bad Request")
        void patch_rejectsInvalidEnum() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));
            mockMvc.perform(get("/api/v1/dashboard/preference")
                    .header("Authorization", "Bearer " + VALID_TOKEN)).andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"theme\":\"PINK\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("font_scale 의 허용 외 값 (예: 1.5) → 400")
        void patch_rejectsInvalidFontScale() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));
            mockMvc.perform(get("/api/v1/dashboard/preference")
                    .header("Authorization", "Bearer " + VALID_TOKEN)).andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"font_scale\":1.5}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =================================================================================
    // §C POST /preference/reset
    // =================================================================================
    @Nested
    @DisplayName("§C POST /preference/reset 스타일 초기화")
    class Reset {

        @Test
        @DisplayName("AC-DP-002-5: 스타일 변경 후 reset → DEFAULT 로 복귀, hidden 은 보존")
        void reset_resetsStyle_preservesHidden() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));
            mockMvc.perform(get("/api/v1/dashboard/preference")
                    .header("Authorization", "Bearer " + VALID_TOKEN)).andExpect(status().isOk());

            // 스타일 + hidden 변경
            mockMvc.perform(patch("/api/v1/dashboard/preference")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"theme\":\"DARK\",\"density\":\"COMPACT\"}"))
                    .andExpect(status().isOk());
            // hidden 직접 주입
            jdbcTemplate.update(
                    "UPDATE user_dashboard_preference SET hidden_widget_instance_ids = ?::jsonb WHERE user_id = ?",
                    "{\"1\":[\"w-a\"]}", userId);

            // reset
            mockMvc.perform(post("/api/v1/dashboard/preference/reset")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("SYSTEM"))
                    .andExpect(jsonPath("$.density").value("NORMAL"))
                    .andExpect(jsonPath("$.hidden_widget_instance_ids.1[0]").value("w-a"));
        }
    }

    // =================================================================================
    // §D PATCH /preference/widgets/{layoutId}/hidden 토글
    // =================================================================================
    @Nested
    @DisplayName("§D 위젯 가시성 토글")
    class Visibility {

        @Test
        @DisplayName("AC-DP-001-1: hidden=true 전송 → hidden 배열에 instance_id 추가")
        void toggle_addsToHidden_whenHiddenTrue() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));

            mockMvc.perform(patch("/api/v1/dashboard/preference/widgets/12/hidden")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instance_id\":\"w-pv-001\",\"hidden\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hidden_widget_instance_ids.12[0]").value("w-pv-001"));
        }

        @Test
        @DisplayName("AC-DP-001-2: hidden=false 전송 → 배열에서 제거")
        void toggle_removesFromHidden_whenHiddenFalse() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));

            // 먼저 추가
            mockMvc.perform(patch("/api/v1/dashboard/preference/widgets/12/hidden")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instance_id\":\"w-pv-001\",\"hidden\":true}"))
                    .andExpect(status().isOk());

            // 제거
            mockMvc.perform(patch("/api/v1/dashboard/preference/widgets/12/hidden")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instance_id\":\"w-pv-001\",\"hidden\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hidden_widget_instance_ids.12").isEmpty());
        }

        @Test
        @DisplayName("AC-DP-001-5: show-all → 특정 layout 의 hidden 을 빈 배열로 초기화")
        void showAll_clearsHiddenForLayout() throws Exception {
            givenValidToken(userId, Set.of("EDITOR"));

            // hidden 에 2개 추가
            mockMvc.perform(patch("/api/v1/dashboard/preference/widgets/5/hidden")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instance_id\":\"w-a\",\"hidden\":true}"))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/v1/dashboard/preference/widgets/5/hidden")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instance_id\":\"w-b\",\"hidden\":true}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/dashboard/preference/widgets/5/show-all")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hidden_widget_instance_ids.5").isEmpty());
        }
    }

    // =================================================================================
    // §E 인증 가드
    // =================================================================================
    @Test
    @DisplayName("AC-DP-001-4: 인증 없이 PATCH 시도 → 401 Unauthorized")
    void patch_requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/dashboard/preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** smoke. */
    @Test
    @DisplayName("smoke: Spring 컨텍스트 + V39 마이그레이션 + Mock 주입 정상")
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
        assertThat(prefMapper).isNotNull();
    }
}
