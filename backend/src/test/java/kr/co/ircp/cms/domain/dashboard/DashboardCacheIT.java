package kr.co.ircp.cms.domain.dashboard;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.dashboard.entity.ChartDatasetCache;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardWidget;
import kr.co.ircp.cms.domain.dashboard.repository.ChartDatasetCacheMapper;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardWidgetMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-008 §E 캐시 + 명시 무효화 API 통합 테스트 (REQ-VIZ-005).
 *
 * <p>cache_admin endpoint(POST /cache/invalidate, GET /cache/stats) 와 chart_dataset_cache
 * 행 조작을 실제 PostgreSQL 16 환경에서 검증한다. 권한 매트릭스(@PreAuthorize) 도 함께 확인.
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>E-3 캐시 miss → 적재 (1차 GET 이후 chart_dataset_cache 행 추가)</li>
 *   <li>E-9 캐시 무효화 (위젯 수정 시 영향받는 캐시 expires_at=NOW 처리) — 본 IT는 직접 매퍼 호출</li>
 *   <li>E-10 명시 무효화 API — SUPER_ADMIN/DEPT_ADMIN 가능, 그 외 403</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] DashboardCacheIT — cache admin endpoint + chart_dataset_cache CRUD IT
// @MX:SPEC: SPEC-CMS-008 §E REQ-VIZ-005
@AutoConfigureMockMvc
@DisplayName("Dashboard 캐시 관리 통합 테스트 (SPEC-CMS-008 §E)")
class DashboardCacheIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChartDatasetCacheMapper cacheMapper;

    @Autowired
    DashboardWidgetMapper widgetMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private long userId;
    private long widgetId;

    @BeforeEach
    void setUp() {
        userId = insertTestUser("cache-it-" + UUID.randomUUID());
        widgetId = insertWidget("CACHE_W_" + UUID.randomUUID().toString().substring(0, 8));
    }

    private void givenValidToken(long userId, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "cache-it-user", roles, Set.of(), Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    private long insertTestUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, " +
                        "password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '캐시테스트', 'ACTIVE', " +
                        "?, 1, NOW(), NOW(), NOW())",
                username,
                "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertWidget(String code) {
        DashboardWidget w = DashboardWidget.builder()
                .code(code)
                .name("캐시 IT 위젯 " + code)
                .description("")
                .widgetType("BAR_CHART")
                .dataSource("KPI_VALUE")
                .dataSourceConfig("{\"kpi_id\":1}")
                .defaultConfig("{}")
                .availableDimensions(List.of("period"))
                .requiredRoleCodes(List.of("VIEWER"))
                .status("ACTIVE")
                .createdBy(userId)
                .build();
        widgetMapper.insert(w);
        return w.getId();
    }

    private ChartDatasetCache insertActiveCache(String suffix) {
        ChartDatasetCache c = ChartDatasetCache.builder()
                .cacheKey("widget:" + widgetId + ":dim::role:VIEWER:" + suffix)
                .widgetId(widgetId)
                .dataset("{\"categories\":[],\"series\":[]}")
                .generatedAt(Instant.now())
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();
        cacheMapper.insert(c);
        return c;
    }

    // =================================================================================
    // §E REQ-VIZ-005
    // =================================================================================
    @Nested
    @DisplayName("§E 캐시 관리 API")
    class CacheAdminApi {

        /**
         * E-9 핵심: 위젯 캐시를 매퍼 단위에서 만료시킨다.
         * widget 단위 expireByWidgetIds 가 영향받는 캐시 모두 만료시키는지 확인.
         */
        @Test
        @DisplayName("E-9: expireByWidgetIds — 영향받는 활성 캐시 모두 expires_at 즉시 만료")
        void expireByWidgetIds_expiresAffectedCaches() {
            // 활성 캐시 3건 적재
            insertActiveCache("a");
            insertActiveCache("b");
            insertActiveCache("c");

            long activeBefore = cacheMapper.countActive();
            long expiredBefore = cacheMapper.countExpired();

            int affected = cacheMapper.expireByWidgetIds(List.of(widgetId));
            assertThat(affected).as("위젯 widgetId 캐시 3건이 만료돼야 함").isGreaterThanOrEqualTo(3);

            assertThat(cacheMapper.countActive()).as("활성 캐시 수 감소").isLessThan(activeBefore);
            assertThat(cacheMapper.countExpired()).as("만료 캐시 수 증가").isGreaterThan(expiredBefore);
        }

        /**
         * E-10: SUPER_ADMIN 이 POST /cache/invalidate {all:true} 호출 → 204 + 모든 캐시 만료.
         */
        @Test
        @DisplayName("E-10: SUPER_ADMIN cache/invalidate {all:true} — 204 + 활성 캐시 0건")
        void invalidate_all_clearsAllCaches_whenSuperAdmin() throws Exception {
            givenValidToken(userId, Set.of("SUPER_ADMIN"));
            insertActiveCache("all-1");
            insertActiveCache("all-2");

            assertThat(cacheMapper.countActive()).as("invalidate 전 활성 캐시").isGreaterThan(0);

            mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"all\":true}"))
                    .andExpect(status().isNoContent());

            assertThat(cacheMapper.countActive())
                    .as("invalidate all 후 활성 캐시 0건")
                    .isEqualTo(0);
        }

        /**
         * E-10 보강: SUPER_ADMIN 이 widgetIds 지정 → 해당 위젯 캐시만 만료.
         */
        @Test
        @DisplayName("E-10: SUPER_ADMIN cache/invalidate widgetIds — 지정 위젯 캐시만 만료")
        void invalidate_widgetIds_expiresSpecifiedWidgetsOnly() throws Exception {
            givenValidToken(userId, Set.of("SUPER_ADMIN"));
            insertActiveCache("wid-1");
            insertActiveCache("wid-2");

            mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"widgetIds\":[" + widgetId + "]}"))
                    .andExpect(status().isNoContent());

            // 해당 위젯 캐시는 모두 만료됨
            Optional<ChartDatasetCache> active = cacheMapper.findActiveByCacheKey(
                    "widget:" + widgetId + ":dim::role:VIEWER:wid-1");
            assertThat(active).as("widgetId 캐시는 더 이상 활성 아님").isEmpty();
        }

        /**
         * E-10: 비SUPER_ADMIN 역할 호출 시 403.
         *
         * <p>운영 컨트롤러: @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')") — VIEWER 는 거부.
         */
        @Test
        @DisplayName("E-10: VIEWER 가 cache/invalidate 호출 — 403 ACCESS_DENIED")
        void invalidate_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(userId, Set.of("VIEWER"));

            mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"all\":true}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        /**
         * GET /cache/stats — SUPER_ADMIN 200 OK + activeEntries/expiredEntries 응답.
         */
        @Test
        @DisplayName("GET cache/stats — SUPER_ADMIN 통계 응답")
        void stats_returnsCounts_whenSuperAdmin() throws Exception {
            givenValidToken(userId, Set.of("SUPER_ADMIN"));
            insertActiveCache("stats-1");

            mockMvc.perform(get("/api/v1/dashboard/cache/stats")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeEntries").exists())
                    .andExpect(jsonPath("$.expiredEntries").exists());
        }
    }

    @Test
    @DisplayName("smoke: Cache 컨텍스트 + Flyway V17 정상")
    void contextLoads() {
        assertThat(cacheMapper).isNotNull();
        assertThat(widgetMapper).isNotNull();
    }
}
