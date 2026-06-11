package kr.co.ircp.cms.domain.dashboard.kpi;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.dashboard.kpi.mapper.KpiQueryMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 API 통합 테스트 (AdminKpiController).
 *
 * <p>실제 PostgreSQL 16 + Flyway V17(kpi_definition/kpi_value/chart_dataset_cache) + V45 환경에서
 * {@code GET /api/v1/admin/kpi/values} 및 {@code GET /api/v1/admin/kpi/conversion-funnel} 의
 * JSONB 필터·캐시·권한·인젝션 방어·PII 비노출을 운영 동등 보안 체인으로 검증한다.
 *
 * <p>실제 스키마 기준 설계 결정(스펙 요약과의 차이):
 * <ul>
 *   <li>kpi_definition 에는 granularity/unit/is_active/formula 컬럼이 없다. 컬럼은
 *       code/name/description/calculation_query/refresh_interval_min/status 만 존재.</li>
 *   <li>기간 그래뉼래리티는 DB 컬럼이 아니라 dimension JSONB 의 키로 인코딩된다
 *       (일별 {"date":...}, 주별 {"week":...}, 월별 {"month":...}, 분기 {"quarter":...}, 연 {"year":...}).
 *       따라서 periodUnit 필터는 {@code jsonb_exists(dimension, key)} 로 처리한다.</li>
 *   <li>fromDate/toDate 는 kpi_value.calculated_at 범위로 필터한다(별도 일자 컬럼 부재).</li>
 * </ul>
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>AC-004 멀티조건 JSONB containment 필터</li>
 *   <li>AC-005 빈 결과 → 200 + 빈 items + filters 메타</li>
 *   <li>AC-006 전환율 데이터 미존재 → dataState=PREPARING</li>
 *   <li>AC-013 캐시 히트 (동일 조회 5분 내 재집계 회피 — KpiQueryMapper.search 1회만 호출)</li>
 *   <li>AC-014 비ADMIN → 403</li>
 *   <li>AC-018 그래뉼래리티 필터 (week 만 반환)</li>
 *   <li>AC-019 LIMIT 1000 상한 + totalCount/hasMore 메타</li>
 *   <li>AC-021 DDL/DML 인젝션 → 400 (집계 쿼리 미실행)</li>
 *   <li>AC-022 PII 비노출 (user_id/client_ip 부재)</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] AdminKpiControllerIT — KPI 조회 API IT (PostgreSQL Singleton Container)
// @MX:SPEC: SPEC-CMS-KPI-001 Phase 2 (AC-004~022 subset)
@AutoConfigureMockMvc
@DisplayName("SPEC-CMS-KPI-001 Phase 2 KPI 조회 API IT (AdminKpiController)")
class AdminKpiControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    /** AC-013: 캐시 히트 시 DB 재조회가 일어나지 않음을 검증하기 위한 Spy. */
    @MockitoSpyBean
    KpiQueryMapper kpiQueryMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private long testUserId;
    private Long usageKpiId;

    @BeforeEach
    void setUp() {
        testUserId = insertTestUser("kpi-it-" + UUID.randomUUID());
        // 조회 산출물 격리
        jdbc.update("DELETE FROM chart_dataset_cache WHERE cache_key LIKE 'kpi:%'");
        // 전용 KPI 정의 1건 보장 (격리를 위해 unique code)
        String code = "IT_USAGE_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO kpi_definition (code, name, description, calculation_query, status) "
                        + "VALUES (?, '기능 사용률 IT', '테스트', 'SELECT 1', 'ACTIVE')",
                code);
        usageKpiId = jdbc.queryForObject(
                "SELECT id FROM kpi_definition WHERE code = ?", Long.class, code);
        this.kpiCode = code;
    }

    private String kpiCode;

    private void givenToken(Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                testUserId, "kpi-it-user", roles, Set.of(), Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    private long insertTestUser(String username) {
        jdbc.update(
                "INSERT INTO users (username, password_hash, name, status, "
                        + "email_hmac, email_key_version, "
                        + "password_changed_at, created_at, updated_at) "
                        + "VALUES (?, 'test-hash', '테스트사용자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    /** kpi_value 행 시드. dimension 은 JSON 문자열로 전달. */
    private void insertKpiValue(Long kpiId, String dimensionJson, double value) {
        jdbc.update(
                "INSERT INTO kpi_value (kpi_id, dimension, value_numeric, calculated_at) "
                        + "VALUES (?, ?::jsonb, ?, ?) "
                        + "ON CONFLICT (kpi_id, dimension) DO UPDATE SET value_numeric = EXCLUDED.value_numeric",
                kpiId, dimensionJson, value,
                OffsetDateTime.of(2026, 6, 10, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    // ─── AC-004 멀티조건 JSONB 필터 ───────────────────────────────────────────
    @Test
    @DisplayName("AC-004: dimensionJson containment 로 정확히 일치하는 KPI 만 반환")
    void ac004_multiConditionJsonbFilter() throws Exception {
        givenToken(Set.of("ADMIN"));
        insertKpiValue(usageKpiId, "{\"month\":\"2026-06\",\"feature\":\"board\",\"industry\":\"it\"}", 0.42);
        insertKpiValue(usageKpiId, "{\"month\":\"2026-06\",\"feature\":\"search\",\"industry\":\"it\"}", 0.91);

        mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode)
                        .param("dimensionJson", "{\"feature\":\"board\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].value").value(0.42))
                .andExpect(jsonPath("$.items[0].dataState").value("READY"));
    }

    // ─── AC-005 빈 결과 ──────────────────────────────────────────────────────
    @Test
    @DisplayName("AC-005: 일치 데이터 없음 → 200 + 빈 items + filters 메타")
    void ac005_emptyResult_returns200WithMeta() throws Exception {
        givenToken(Set.of("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode)
                        .param("dimensionJson", "{\"feature\":\"NONEXISTENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.filters").exists());
    }

    // ─── AC-006 전환율 PREPARING ──────────────────────────────────────────────
    @Test
    @DisplayName("AC-006: policy_match_stats_monthly 데이터 부재 → dataState=PREPARING")
    void ac006_conversionFunnel_preparing_whenNoData() throws Exception {
        givenToken(Set.of("ADMIN"));
        // policy_match_stats_monthly 비움 → PREPARING 기대
        jdbc.update("DELETE FROM policy_match_stats_monthly");

        mockMvc.perform(get("/api/v1/admin/kpi/conversion-funnel")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("statMonth", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataState").value("PREPARING"))
                .andExpect(jsonPath("$.value").doesNotExist());
    }

    // ─── AC-013 캐시 히트 ─────────────────────────────────────────────────────
    @Test
    @DisplayName("AC-013: 동일 조회 5분 내 재요청 시 KpiQueryMapper.search 1회만 호출 (캐시 히트)")
    void ac013_cacheHit_doesNotRequeryDb() throws Exception {
        givenToken(Set.of("ADMIN"));
        insertKpiValue(usageKpiId, "{\"month\":\"2026-06\",\"feature\":\"board\"}", 0.55);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/admin/kpi/values")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .param("kpiCode", kpiCode)
                            .param("dimensionJson", "{\"feature\":\"board\"}"))
                    .andExpect(status().isOk());
        }
        // 2회 요청 중 DB 검색은 1회만 (2회차는 캐시 히트)
        verify(kpiQueryMapper, times(1)).search(any());
    }

    // ─── AC-014 비ADMIN 403 ───────────────────────────────────────────────────
    @Test
    @DisplayName("AC-014: VIEWER 권한으로 조회 시 403 Forbidden")
    void ac014_nonAdmin_returns403() throws Exception {
        givenToken(Set.of("VIEWER"));

        mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isForbidden());
    }

    // ─── AC-018 그래뉼래리티 필터 ─────────────────────────────────────────────
    @Test
    @DisplayName("AC-018: granularity=weekly → week 차원만 반환, month/year 제외")
    void ac018_granularityFilter() throws Exception {
        givenToken(Set.of("ADMIN"));
        insertKpiValue(usageKpiId, "{\"week\":\"2026-W24\",\"feature\":\"board\"}", 0.10);
        insertKpiValue(usageKpiId, "{\"month\":\"2026-06\",\"feature\":\"board\"}", 0.20);
        insertKpiValue(usageKpiId, "{\"year\":\"2026\",\"feature\":\"board\"}", 0.30);

        mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode)
                        .param("granularity", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].value").value(0.10));
    }

    // ─── AC-019 LIMIT 1000 ────────────────────────────────────────────────────
    @Test
    @DisplayName("AC-019: 1000행 초과 시 최대 1000행 반환 + hasMore=true 메타")
    void ac019_limitCeiling_paginationMeta() throws Exception {
        givenToken(Set.of("ADMIN"));
        // 1001행 시드 (서로 다른 dimension 키로 unique 보장)
        StringBuilder batch = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            insertKpiValue(usageKpiId, "{\"month\":\"2026-06\",\"seq\":\"" + i + "\"}", i);
        }

        mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode)
                        .param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1000))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    // ─── AC-021 SQL 인젝션 방어 ───────────────────────────────────────────────
    @Test
    @DisplayName("AC-021: dimensionJson 에 DDL/DML 패턴 포함 → 400 (집계 미실행)")
    void ac021_sqlInjection_rejected400() throws Exception {
        givenToken(Set.of("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode)
                        .param("dimensionJson", "{\"feature\":\"x\"}; DROP TABLE kpi_value"))
                .andExpect(status().isBadRequest());

        // 집계 쿼리가 실행되지 않았어야 함
        verify(kpiQueryMapper, times(0)).search(any());
    }

    // ─── AC-022 PII 비노출 ────────────────────────────────────────────────────
    @Test
    @DisplayName("AC-022: 응답 본문에 user_id/client_ip 등 PII 컬럼 미포함")
    void ac022_noPiiInResponse() throws Exception {
        givenToken(Set.of("ADMIN"));
        insertKpiValue(usageKpiId, "{\"month\":\"2026-06\",\"feature\":\"board\"}", 0.77);

        String body = mockMvc.perform(get("/api/v1/admin/kpi/values")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("user_id")
                .doesNotContain("client_ip")
                .doesNotContain("userId")
                .doesNotContain("clientIp")
                .doesNotContain("ip_hash");
    }
}
