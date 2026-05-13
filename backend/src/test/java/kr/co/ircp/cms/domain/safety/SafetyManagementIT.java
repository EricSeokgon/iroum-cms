package kr.co.ircp.cms.domain.safety;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-006 안전경영 도메인 IT.
 *
 * <p>커버 영역:
 * <ul>
 *   <li>§REQ-SAFETY-001 — 사고사례 CRUD (5 AC)</li>
 *   <li>§REQ-SAFETY-002 — 프로필/매칭 인증 게이트 (2 AC)</li>
 *   <li>§REQ-SAFETY-003 — 보고서 인증 게이트 (2 AC)</li>
 *   <li>§REQ-SAFETY-005 — 템플릿 CRUD + 키워드 (5 + 2 AC)</li>
 * </ul>
 *
 * <p>설계 이슈: {@code @AuthenticationPrincipal Long companyId} 패턴은 JwtPrincipal이 실제
 * principal로 설정되므로 항상 {@code null} 주입됨. REQ-SAFETY-002/003/004는 인증 게이트만 검증하고
 * 인증된 happy path는 SecurityConfig 리팩토링 후 별도 SPEC에서 작성한다.
 */
// @MX:NOTE: [AUTO] SafetyManagementIT — SPEC-CMS-006 §A~§E IT (사고사례·템플릿·키워드·인증 게이트)
// @MX:NOTE: [AUTO] @AuthenticationPrincipal Long companyId 설계 이슈 — JwtPrincipal이 실제 principal로 설정되므로 companyId=null 주입됨
// @MX:TODO: [AUTO] REQ-SAFETY-002/003/004 companyId 의존 엔드포인트 — SecurityConfig/Controller에서 JwtPrincipal 기반으로 리팩토링 후 완전 IT 작성 필요
// @MX:SPEC: SPEC-CMS-006
@AutoConfigureMockMvc
@DisplayName("안전경영 도메인 IT (SPEC-CMS-006)")
class SafetyManagementIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-safety-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;

    @BeforeEach
    void setUp() {
        // V15 안전경영 스키마 테이블 정리 — 자식(FK) 먼저 삭제
        // company_safety_profile/report/match는 다른 IT 클래스에서 사용하지 않으므로 안전하게 정리 가능
        jdbcTemplate.update("DELETE FROM safety_check_result");
        jdbcTemplate.update("DELETE FROM safety_checklist_item");
        jdbcTemplate.update("DELETE FROM safety_guideline_report");
        jdbcTemplate.update("DELETE FROM safety_guideline_template");
        jdbcTemplate.update("DELETE FROM safety_match_result");
        jdbcTemplate.update("DELETE FROM company_safety_profile");
        jdbcTemplate.update("DELETE FROM safety_incident_keyword");
        jdbcTemplate.update("DELETE FROM safety_keyword_synonym");
        jdbcTemplate.update("DELETE FROM safety_keyword");
        jdbcTemplate.update("DELETE FROM safety_incident");

        adminId = insertUser("safety-admin-" + uid());
        givenAdminToken();
    }

    // ─── §REQ-001 사고사례 CRUD (5 AC) ───────────────────────────────────────

    @Nested
    @DisplayName("§REQ-001: 사고사례 CRUD (REQ-SAFETY-001)")
    class IncidentCrud {

        @Test
        @DisplayName("REQ-SAFETY-001-D-5 — GET /incidents 인증 없이 호출 시 401")
        void list_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/safety/incidents"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REQ-SAFETY-001-D-5 — POST /admin/incidents — 정상 생성 201 + body")
        void createIncident_withAuth_returns201() throws Exception {
            String body = """
                    {
                      "sourceType": "KOSHA_OPENAPI",
                      "industryCode": "C25",
                      "incidentType": "FALL",
                      "occurredAt": "2025-01-15T10:30:00Z",
                      "severity": "HIGH",
                      "casualties": 1,
                      "location": "서울시 강남구 현장",
                      "summary": "고소작업 중 추락 사고",
                      "detailedCause": "안전대 미착용",
                      "preventionLesson": "안전대 착용 의무화"
                    }
                    """;
            mockMvc.perform(post("/api/v1/safety/admin/incidents")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.summary").value("고소작업 중 추락 사고"))
                    .andExpect(jsonPath("$.severity").value("HIGH"));
        }

        @Test
        @DisplayName("REQ-SAFETY-001-D-1 — GET /incidents 인증 후 200 + content 배열")
        void listIncidents_withAuth_returns200() throws Exception {
            // 사전 데이터: 직접 INSERT로 1건 생성
            insertIncident("C25", "FALL", "HIGH", "사전 등록 사고");

            mockMvc.perform(get("/api/v1/safety/incidents")
                            .header("Authorization", TOKEN)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].summary").value("사전 등록 사고"));
        }

        @Test
        @DisplayName("REQ-SAFETY-001-D-2 — GET /incidents/{id} 정상 조회 200 + summary 필드")
        void getIncidentById_withAuth_returns200() throws Exception {
            long id = insertIncident("C25", "FIRE", "CRITICAL", "화재 사고 상세");

            mockMvc.perform(get("/api/v1/safety/incidents/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((int) id))
                    .andExpect(jsonPath("$.summary").value("화재 사고 상세"))
                    .andExpect(jsonPath("$.severity").value("CRITICAL"));
        }

        @Test
        @DisplayName("REQ-SAFETY-001-D-5 — PUT /admin/incidents/{id} — summary 수정 200")
        void updateIncident_withAuth_returns200() throws Exception {
            long id = insertIncident("C25", "FALL", "MEDIUM", "수정 전 요약");

            String body = """
                    {
                      "summary": "수정 후 요약",
                      "severity": "HIGH"
                    }
                    """;
            mockMvc.perform(put("/api/v1/safety/admin/incidents/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary").value("수정 후 요약"))
                    .andExpect(jsonPath("$.severity").value("HIGH"));
        }
    }

    // ─── §REQ-002 프로필/매칭 인증 게이트 (2 AC) ──────────────────────────────

    @Nested
    @DisplayName("§REQ-002: 프로필/매칭 인증 게이트 (REQ-SAFETY-002)")
    class ProfileAuthGate {

        @Test
        @DisplayName("REQ-SAFETY-002 — POST /profiles 인증 없이 호출 시 401")
        // @MX:NOTE: [AUTO] companyId 의존 엔드포인트 — 인증 게이트만 검증, happy path는 별도 SPEC
        void upsertProfile_withoutAuth_returns401() throws Exception {
            String body = """
                    {"industryCode":"C25","employeeCount":50}
                    """;
            mockMvc.perform(post("/api/v1/safety/profiles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REQ-SAFETY-002-D-3 — POST /match 인증 없이 호출 시 401")
        void match_withoutAuth_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/safety/match")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── §REQ-003 보고서 인증 게이트 (2 AC) ───────────────────────────────────

    @Nested
    @DisplayName("§REQ-003: 보고서 인증 게이트 (REQ-SAFETY-003)")
    class ReportAuthGate {

        @Test
        @DisplayName("REQ-SAFETY-003 — POST /reports 인증 없이 호출 시 401")
        // @MX:NOTE: [AUTO] companyId 의존 엔드포인트 — 인증 게이트만 검증
        void createReport_withoutAuth_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/safety/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REQ-SAFETY-003-D-5 — GET /reports/me 인증 없이 호출 시 401")
        void getMyReports_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/safety/reports/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── §REQ-005 템플릿 CRUD (5 AC) ──────────────────────────────────────────

    @Nested
    @DisplayName("§REQ-005: 가이드라인 템플릿 CRUD (REQ-SAFETY-005)")
    class TemplateCrud {

        @Test
        @DisplayName("REQ-SAFETY-005-D-1 — POST /admin/templates — 정상 생성 201")
        // @MX:NOTE: [AUTO] createdBy는 @AuthenticationPrincipal Long, DB NULL 허용 → companyId=null이어도 통과
        void createTemplate_withAuth_returns201() throws Exception {
            String code = "T-" + uid();
            String body = """
                    {
                      "code": "%s",
                      "name": "건설업 추락 예방 가이드",
                      "description": "건설업 고소작업 가이드",
                      "applicableIndustryCodes": ["C25", "F41"],
                      "applicableGrades": ["A", "B"],
                      "structure": "{\\"sections\\":[]}",
                      "reviewStatus": "DRAFT"
                    }
                    """.formatted(code);
            mockMvc.perform(post("/api/v1/safety/admin/templates")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.name").value("건설업 추락 예방 가이드"));
        }

        @Test
        @DisplayName("REQ-SAFETY-005-D-1 — GET /admin/templates — 목록 조회 200 + 배열")
        void listTemplates_withAuth_returns200() throws Exception {
            insertTemplate("LIST-" + uid(), "리스트 템플릿");

            mockMvc.perform(get("/api/v1/safety/admin/templates")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("REQ-SAFETY-005-D-1 — GET /admin/templates/{id} — 단건 조회 200")
        void getTemplate_withAuth_returns200() throws Exception {
            long id = insertTemplate("GET-" + uid(), "조회 템플릿");

            mockMvc.perform(get("/api/v1/safety/admin/templates/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((int) id))
                    .andExpect(jsonPath("$.name").value("조회 템플릿"));
        }

        @Test
        @DisplayName("REQ-SAFETY-005-D-2 — PUT /admin/templates/{id} — 새 버전 릴리즈 200")
        // @MX:NOTE: [AUTO] releaseNewVersion은 신규 버전 row 생성 또는 동일 row 갱신 — 서비스 구현에 따라 다름
        void updateTemplate_withAuth_returns200() throws Exception {
            String code = "PUT-" + uid();
            long id = insertTemplate(code, "수정 전 이름");

            String body = """
                    {
                      "code": "%s",
                      "name": "수정 후 이름",
                      "description": "v2.0 릴리즈",
                      "applicableIndustryCodes": ["C25"],
                      "applicableGrades": ["A"],
                      "structure": "{\\"v\\":2}",
                      "reviewStatus": "APPROVED"
                    }
                    """.formatted(code);
            mockMvc.perform(put("/api/v1/safety/admin/templates/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("수정 후 이름"));
        }

        @Test
        @DisplayName("REQ-SAFETY-005-D-1 — DELETE /admin/templates/{id} — archive 204")
        void deleteTemplate_withAuth_returns204() throws Exception {
            long id = insertTemplate("DEL-" + uid(), "삭제 템플릿");

            mockMvc.perform(delete("/api/v1/safety/admin/templates/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }
    }

    // ─── §Keywords (bonus, REQ-SAFETY-005 인접) ────────────────────────────────

    @Nested
    @DisplayName("§Keywords: 키워드 사전 (REQ-SAFETY-002 사전 관리)")
    class KeywordCrud {

        @Test
        @DisplayName("REQ-SAFETY-002 — POST /admin/keywords — 정상 생성 201")
        void createKeyword_withAuth_returns201() throws Exception {
            String code = "KW-" + uid();
            String body = """
                    {
                      "category": "PROCESS",
                      "code": "%s",
                      "term": "고소작업",
                      "description": "2m 이상 작업",
                      "synonyms": ["고소", "high-altitude"]
                    }
                    """.formatted(code);
            mockMvc.perform(post("/api/v1/safety/admin/keywords")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.term").value("고소작업"));
        }

        @Test
        @DisplayName("REQ-SAFETY-002 — GET /admin/keywords — 목록 조회 200 + 배열")
        void listKeywords_withAuth_returns200() throws Exception {
            insertKeyword("PROCESS", "LIST-KW-" + uid(), "사전 등록 키워드");

            mockMvc.perform(get("/api/v1/safety/admin/keywords")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        // SQL/JSON 키로 적합하도록 하이픈 제거, 8자 영숫자
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private void givenAdminToken() {
        // SUPER_ADMIN 권한 — 안전경영 admin 엔드포인트 통과용
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "safety-admin-" + adminId,
                Set.of("ADMIN", "SUPER_ADMIN"),
                Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        // V24 PII 마이그레이션 컬럼 충족 — CodeSystemIT 동일 패턴
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '안전테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    /**
     * safety_incident 직접 INSERT — V15 스키마.
     */
    private long insertIncident(String industryCode, String incidentType, String severity, String summary) {
        jdbcTemplate.update(
                "INSERT INTO safety_incident (source_type, industry_code, incident_type, " +
                        "occurred_at, severity, casualties, summary, status, created_at, updated_at) " +
                        "VALUES ('KOSHA_OPENAPI', ?, ?, NOW(), ?, 1, ?, 'PUBLISHED', NOW(), NOW())",
                industryCode, incidentType, severity, summary);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM safety_incident WHERE summary = ? ORDER BY id DESC LIMIT 1",
                Long.class, summary);
        return id == null ? -1L : id;
    }

    /**
     * safety_guideline_template 직접 INSERT — V15 스키마.
     * applicable_industry_codes/grades는 TEXT[] 타입이므로 PostgreSQL array literal 사용.
     */
    private long insertTemplate(String code, String name) {
        jdbcTemplate.update(
                "INSERT INTO safety_guideline_template " +
                        "(code, name, description, applicable_industry_codes, applicable_grades, " +
                        " structure, status, version, created_at) " +
                        "VALUES (?, ?, '테스트 템플릿', '{C25}', '{A,B}', " +
                        " '{\"sections\":[]}'::jsonb, 'DRAFT', 'v1.0', NOW())",
                code, name);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM safety_guideline_template WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    /**
     * safety_keyword 직접 INSERT — V15 스키마.
     */
    private long insertKeyword(String category, String code, String term) {
        jdbcTemplate.update(
                "INSERT INTO safety_keyword (category, code, term, status, created_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW())",
                category, code, term);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM safety_keyword WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }
}
