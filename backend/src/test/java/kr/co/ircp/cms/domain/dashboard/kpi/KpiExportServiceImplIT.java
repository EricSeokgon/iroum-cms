package kr.co.ircp.cms.domain.dashboard.kpi;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.dashboard.kpi.service.KpiExportService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * SPEC-CMS-KPI-001 Phase 3: KPI Excel 내보내기 통합 테스트 (KpiExportServiceImpl + KpiExportController).
 *
 * <p>실제 PostgreSQL 16 + Flyway V17(kpi_definition/kpi_value/export_history) 환경에서
 * {@code POST /api/v1/admin/kpi/export} 및 {@code GET /api/v1/admin/kpi/export/download} 의
 * 동기/비동기 분기, SXSSFWorkbook 다중 시트 청킹, 크기 상한, 감사 로그, HMAC 서명 다운로드를
 * 운영 동등 보안 체인으로 검증한다.
 *
 * <p>실제 스키마 기준 설계 결정:
 * <ul>
 *   <li>kpi_value 는 dimension JSONB 로 차원/기간을 인코딩. calculated_at 는 TIMESTAMPTZ.</li>
 *   <li>동기/비동기 분기 임계값(syncThreshold), 시트당 최대 행(maxRowsPerSheet), 전체 상한
 *       (maxExportRows)은 프로퍼티로 주입 가능하며, 본 IT 는 작은 값으로 재정의하여
 *       대용량 시나리오를 빠르게 재현한다.</li>
 *   <li>audit_log 에는 detail 컬럼이 없어 @AuditLog(captureArgs/captureReturn) 로
 *       조회 조건과 행 수를 before/after JSONB 로 적재한다(AC-015).</li>
 * </ul>
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>AC-007 동기 export(&lt;10k) → 200 + xlsx content-type + 유효한 Excel(헤더 8열)</li>
 *   <li>AC-008 비동기 export(&gt;=10k) → 202 + {jobId, status:PROCESSING}</li>
 *   <li>AC-009 다중 시트 청킹(maxRowsPerSheet 초과) → Sheet1/Sheet2 분할</li>
 *   <li>AC-010 maxExportRows 초과 → 400</li>
 *   <li>AC-015 @AuditLog → audit_log 에 EXPORT 액션 + 조건 + 행 수 적재</li>
 *   <li>AC-020 HMAC 다운로드 — 유효 서명 200/파일, 위조 서명 400, 타인 403</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] KpiExportServiceImplIT — KPI Excel export 6 AC GREEN IT (PostgreSQL Singleton Container)
// @MX:SPEC: SPEC-CMS-KPI-001 Phase 3 (AC-007/008/009/010/015/020)
@AutoConfigureMockMvc
@DisplayName("SPEC-CMS-KPI-001 Phase 3 KPI Excel 내보내기 IT")
class KpiExportServiceImplIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void exportThresholds(DynamicPropertyRegistry registry) {
        // 대용량 시나리오를 빠르게 재현하기 위한 소형 임계값 주입.
        // 동기/비동기 분기 10행, 시트당 5행, 전체 상한 100행.
        registry.add("iroum.kpi.export.sync-threshold", () -> "10");
        registry.add("iroum.kpi.export.max-rows-per-sheet", () -> "5");
        registry.add("iroum.kpi.export.max-export-rows", () -> "100");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KpiExportService kpiExportService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private long testUserId;
    private long otherUserId;
    private Long usageKpiId;
    private String kpiCode;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        testUserId = insertTestUser("kex-a-" + suffix);
        otherUserId = insertTestUser("kex-b-" + suffix);

        String code = "IT_EXPORT_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO kpi_definition (code, name, description, calculation_query, status) "
                        + "VALUES (?, '기능 사용률 Export IT', '테스트', 'SELECT 1', 'ACTIVE')",
                code);
        usageKpiId = jdbc.queryForObject(
                "SELECT id FROM kpi_definition WHERE code = ?", Long.class, code);
        this.kpiCode = code;
    }

    private void givenToken(long userId, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "kpi-export-it-user", roles, Set.of(), Instant.now().plusSeconds(900));
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

    /** kpi_value 행 시드. seq 로 unique dimension 보장. */
    private void seedRows(int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update(
                    "INSERT INTO kpi_value (kpi_id, dimension, value_numeric, calculated_at) "
                            + "VALUES (?, ?::jsonb, ?, ?) "
                            + "ON CONFLICT (kpi_id, dimension) DO UPDATE SET value_numeric = EXCLUDED.value_numeric",
                    usageKpiId,
                    "{\"month\":\"2026-06\",\"seq\":\"" + i + "\"}",
                    (double) i,
                    OffsetDateTime.of(2026, 6, 10, 12, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    // ─── AC-007 동기 export ───────────────────────────────────────────────────
    @Test
    @DisplayName("AC-007: 동기 export(<10행) → 200 + xlsx content-type + 헤더 8열 유효 Excel")
    void ac007_syncExport_returnsValidExcel() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(3);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isOk())
                .andExpect(content -> assertThat(content.getResponse().getContentType())
                        .contains("spreadsheetml.sheet"))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getLastCellNum()).isEqualTo((short) 8);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("KPI Code");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("KPI Name");
            // 헤더 1줄 + 데이터 3줄
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }
    }

    // ─── AC-008 비동기 export ─────────────────────────────────────────────────
    @Test
    @DisplayName("AC-008: 비동기 export(>=10행) → 202 + {jobId, status:PROCESSING}")
    void ac008_asyncExport_returns202WithJob() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(15); // syncThreshold(10) 이상 → 비동기

        mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    // ─── AC-009 다중 시트 청킹 ────────────────────────────────────────────────
    @Test
    @DisplayName("AC-009: maxRowsPerSheet(5) 초과 행을 generateWorkbook 이 Sheet1/Sheet2 로 분할")
    void ac009_multiSheetChunking() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(7); // 시트당 5행 → 5 + 2 → 2개 시트

        kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest req =
                new kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest(
                        kpiCode, null, null, null, null, 0, 100);
        byte[] body = kpiExportService.generateWorkbook(req);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            // 1번 시트: 헤더 1 + 데이터 5 = lastRowNum 5
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(5);
            // 2번 시트: 헤더 1 + 데이터 2 = lastRowNum 2
            assertThat(wb.getSheetAt(1).getLastRowNum()).isEqualTo(2);
        }
    }

    // ─── AC-010 크기 상한 초과 ────────────────────────────────────────────────
    @Test
    @DisplayName("AC-010: maxExportRows(100) 초과 → 400")
    void ac010_exceedsMaxExportRows_returns400() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(101); // maxExportRows(100) 초과

        mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isBadRequest());
    }

    // ─── AC-014 비ADMIN 403 (권한 invariant 회귀) ─────────────────────────────
    @Test
    @DisplayName("AC-014: VIEWER 권한으로 export 시 403 Forbidden")
    void ac014_nonAdmin_returns403() throws Exception {
        givenToken(testUserId, Set.of("VIEWER"));

        mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isForbidden());
    }

    // ─── AC-015 감사 로그 ─────────────────────────────────────────────────────
    @Test
    @DisplayName("AC-015: 동기 export 성공 시 audit_log 에 EXPORT 액션 + 조건/행수 적재")
    void ac015_auditLogRecordsExport() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(3);

        long before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'EXPORT' AND actor_id = ?",
                Long.class, testUserId);

        mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isOk());

        // @AuditLog 비동기 적재 안정화 대기
        org.awaitility.Awaitility.await()
                .atMost(5, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    long after = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM audit_log WHERE action = 'EXPORT' AND actor_id = ?",
                            Long.class, testUserId);
                    assertThat(after).isEqualTo(before + 1);
                });

        // 조건(kpiCode) 과 행 수가 before/after JSONB 에 캡처되었는지 검증
        String captured = jdbc.queryForObject(
                "SELECT COALESCE(before_value::text,'') || COALESCE(after_value::text,'') "
                        + "FROM audit_log WHERE action = 'EXPORT' AND actor_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                String.class, testUserId);
        assertThat(captured).contains(kpiCode);
        assertThat(captured).contains("3"); // 행 수 3
    }

    // ─── AC-020 HMAC 다운로드 ─────────────────────────────────────────────────
    @Test
    @DisplayName("AC-020a: 유효 HMAC 서명 다운로드 → 200 + xlsx 파일")
    void ac020_validSignature_downloadsFile() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(15); // 비동기 → 파일 저장 + signed URL

        // 비동기 export 생성 → jobId
        MvcResult created = mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = readJobId(created.getResponse().getContentAsString());

        // 비동기 파일 작성 완료 대기 (status COMPLETED + file_path)
        org.awaitility.Awaitility.await()
                .atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    String status = jdbc.queryForObject(
                            "SELECT status FROM export_history WHERE id = ?", String.class, jobId);
                    assertThat(status).isEqualTo("COMPLETED");
                });

        Instant exp = jdbc.queryForObject(
                "SELECT expires_at FROM export_history WHERE id = ?", Instant.class, jobId);
        String sig = kpiExportService.signFor(jobId, exp);

        MvcResult dl = mockMvc.perform(get("/api/v1/admin/kpi/export/download")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("jobId", String.valueOf(jobId))
                        .param("sig", sig)
                        .param("exp", String.valueOf(exp.getEpochSecond())))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = dl.getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("KPI Code");
        }
    }

    @Test
    @DisplayName("AC-020b: 위조 HMAC 서명 다운로드 → 400")
    void ac020_forgedSignature_returns400() throws Exception {
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(15);

        MvcResult created = mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = readJobId(created.getResponse().getContentAsString());

        Instant exp = jdbc.queryForObject(
                "SELECT expires_at FROM export_history WHERE id = ?", Instant.class, jobId);

        mockMvc.perform(get("/api/v1/admin/kpi/export/download")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("jobId", String.valueOf(jobId))
                        .param("sig", "deadbeef-forged")
                        .param("exp", String.valueOf(exp.getEpochSecond())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-020c: 타인 소유 export 다운로드 시도 → 403")
    void ac020_wrongOwner_returns403() throws Exception {
        // testUser 가 export 생성
        givenToken(testUserId, Set.of("ADMIN"));
        seedRows(15);
        MvcResult created = mockMvc.perform(post("/api/v1/admin/kpi/export")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("kpiCode", kpiCode))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = readJobId(created.getResponse().getContentAsString());

        Instant exp = jdbc.queryForObject(
                "SELECT expires_at FROM export_history WHERE id = ?", Instant.class, jobId);
        String sig = kpiExportService.signFor(jobId, exp);

        // otherUser 가 동일 서명으로 다운로드 시도 → 403 (소유자 불일치)
        givenToken(otherUserId, Set.of("ADMIN"));
        mockMvc.perform(get("/api/v1/admin/kpi/export/download")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("jobId", String.valueOf(jobId))
                        .param("sig", sig)
                        .param("exp", String.valueOf(exp.getEpochSecond())))
                .andExpect(status().isForbidden());
    }

    private long readJobId(String json) {
        int idx = json.indexOf("\"jobId\"");
        int colon = json.indexOf(':', idx);
        int end = json.indexOf(',', colon);
        if (end < 0) end = json.indexOf('}', colon);
        return Long.parseLong(json.substring(colon + 1, end).trim());
    }
}
