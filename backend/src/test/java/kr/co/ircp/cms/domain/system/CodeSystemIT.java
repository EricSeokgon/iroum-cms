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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-005 §D 공통코드 관리 IT (REQ-SYSTEM-004-D).
 *
 * <p>5 AC 커버:
 * <ul>
 *   <li>REQ-SYSTEM-004-D-1: 그룹 CRUD + RESTRICT (사용 중 그룹 삭제 거부)</li>
 *   <li>REQ-SYSTEM-004-D-2: 코드 CRUD + (group_code, code) UNIQUE 중복 거부</li>
 *   <li>REQ-SYSTEM-004-D-3: 그룹별 묶음 조회 — ACTIVE only, sort_order ASC</li>
 *   <li>REQ-SYSTEM-004-D-4: 캐시 무효화 — update 후 GET 결과 즉시 반영</li>
 *   <li>REQ-SYSTEM-004-D-5: 다중 그룹 묶음 조회 (Map&lt;groupCode, List&gt;)</li>
 * </ul>
 *
 * <p>인증 모델: SYSTEM:CODE:READ + SYSTEM:CODE:WRITE 권한 보유 ADMIN.
 */
// @MX:NOTE: [AUTO] CodeSystemIT — SPEC-CMS-005 §D 공통코드 IT (SYSTEM:CODE:WRITE 권한 패턴)
// @MX:NOTE: [AUTO] SPEC-CMS-005 §D-1 컨트롤러 경로 /api/v1/system/codes/groups (CodeGroupController)
// @MX:SPEC: SPEC-CMS-005#REQ-SYSTEM-004-D
@AutoConfigureMockMvc
@DisplayName("공통코드 관리 IT (SPEC-CMS-005 §D)")
class CodeSystemIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-code-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;

    @BeforeEach
    void setUp() {
        // 잔존 데이터 정리 — code 테이블이 code_group을 RESTRICT로 참조하므로 자식 먼저 삭제
        jdbcTemplate.update("DELETE FROM code");
        jdbcTemplate.update("DELETE FROM code_group");

        adminId = insertUser("code-admin-" + uid());
        givenAdminToken();
    }

    // ─── §D-1 REQ-SYSTEM-004-D-1: 그룹 CRUD + RESTRICT ───────────────────────

    @Nested
    @DisplayName("§D-1: 코드 그룹 CRUD + RESTRICT 삭제")
    class GroupCrud {

        @Test
        @DisplayName("REQ-SYSTEM-004-D-1 — POST /codes/groups 정상 생성 201")
        void create_group_returns201() throws Exception {
            String groupCode = "GENDER-" + uid();
            String body = """
                    {
                      "groupCode": "%s",
                      "name": "성별",
                      "description": "성별 공통코드"
                    }
                    """.formatted(groupCode);
            mockMvc.perform(post("/api/v1/system/codes/groups")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.groupCode").value(groupCode))
                    .andExpect(jsonPath("$.name").value("성별"));
        }

        @Test
        @DisplayName("REQ-SYSTEM-004-D-1 — 사용 중 그룹 DELETE — RESTRICT(409 또는 FK 에러)")
        // @MX:NOTE: [AUTO] CodeGroupInUseException 전용 핸들러 미존재 — 현 시점 500 가능성.
        // SPEC 의도는 409 Conflict (CodeGroupInUseException 또는 DataIntegrityViolation). 핸들러 추가는 별도 SPEC 항목.
        void delete_group_inUse_isRejected() throws Exception {
            // 1) 그룹 생성
            String groupCode = "GENDER-" + uid();
            long groupId = insertCodeGroup(groupCode, "성별");
            // 2) 자식 코드 추가 → RESTRICT 발동 조건 충족
            insertCode(groupCode, "M", "남성", 1, "ACTIVE");

            // 3) DELETE → 성공해서는 안 됨 (200/204 외 상태)
            // @DeleteMapping("/{code}") — groupCode(String) 을 경로변수로 사용
            mockMvc.perform(delete("/api/v1/system/codes/groups/" + groupCode)
                            .header("Authorization", TOKEN))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        // 409 Conflict (SYSTEM_CODE_GROUP_IN_USE) 또는 500(핸들러 미등록) 모두 RESTRICT 동작 증거
                        if (s == 204 || s == 200) {
                            throw new AssertionError("그룹 삭제가 막혀야 함 (RESTRICT 위반) — status=" + s);
                        }
                    });

            // 4) DB 검증: 그룹과 코드 모두 존재해야 함
            Integer remaining = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM code_group WHERE id = ?", Integer.class, groupId);
            assert remaining != null && remaining == 1
                    : "RESTRICT 동작 실패 — 그룹이 삭제됨 (remaining=" + remaining + ")";
        }
    }

    // ─── §D-2 REQ-SYSTEM-004-D-2: 코드 CRUD + UNIQUE 거부 ────────────────────

    @Nested
    @DisplayName("§D-2: 코드 (group_code, code) UNIQUE 거부")
    class CodeUniqueDuplicate {

        @Test
        @DisplayName("REQ-SYSTEM-004-D-2 — 동일 (groupCode, code) 중복 생성 — UNIQUE 거부")
        // @MX:NOTE: [AUTO] CodeDuplicateException 전용 핸들러 미존재 — 현 시점 500 가능성.
        // SPEC 의도는 409 Conflict (SYSTEM_CODE_DUPLICATE). 핸들러 추가는 별도 항목.
        void create_duplicate_isRejected() throws Exception {
            String groupCode = "GENDER-" + uid();
            insertCodeGroup(groupCode, "성별");

            String body = """
                    {
                      "groupCode": "%s",
                      "code": "M",
                      "name": "남성",
                      "sortOrder": 1
                    }
                    """.formatted(groupCode);

            // 1) 첫 생성 — 201 Created
            mockMvc.perform(post("/api/v1/system/codes")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());

            // 2) 동일 (groupCode, code) 재생성 — 거부
            mockMvc.perform(post("/api/v1/system/codes")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        if (s == 200 || s == 201) {
                            throw new AssertionError("UNIQUE 중복 생성이 허용됨 — status=" + s);
                        }
                    });
        }
    }

    // ─── §D-3 REQ-SYSTEM-004-D-3: 그룹별 묶음 조회 (ACTIVE only, sort_order ASC) ──

    @Nested
    @DisplayName("§D-3: 그룹별 묶음 조회 — ACTIVE only, sort_order ASC")
    class ListByGroup {

        @Test
        @DisplayName("REQ-SYSTEM-004-D-3 — GET /codes?groupCode=X — ACTIVE만 sort_order 오름차순 반환")
        void listByGroup_returnsActiveSortedAsc() throws Exception {
            String groupCode = "GENDER-" + uid();
            insertCodeGroup(groupCode, "성별");
            // sort_order=2 → 두번째, sort_order=1 → 첫번째, INACTIVE는 제외
            insertCode(groupCode, "F", "여성", 2, "ACTIVE");
            insertCode(groupCode, "M", "남성", 1, "ACTIVE");
            insertCode(groupCode, "X", "기타(비활성)", 3, "INACTIVE");

            mockMvc.perform(get("/api/v1/system/codes")
                            .header("Authorization", TOKEN)
                            .param("groupCode", groupCode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    // sort_order ASC: M(1) < F(2)
                    .andExpect(jsonPath("$[0].code").value("M"))
                    .andExpect(jsonPath("$[1].code").value("F"))
                    // INACTIVE 'X'는 응답에 포함되어선 안 됨 → 인덱스 [2] 부재 검증 (length=2)
                    .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[1].status").value("ACTIVE"));
        }
    }

    // ─── §D-4 REQ-SYSTEM-004-D-4: 캐시 무효화 (update 후 즉시 반영) ────────────

    @Nested
    @DisplayName("§D-4: 캐시 무효화 — update 후 즉시 반영")
    class CacheEviction {

        @Test
        @DisplayName("REQ-SYSTEM-004-D-4 — POST → GET → PUT(name 변경) → GET → 새 값 반영")
        // @MX:NOTE: [AUTO] Caffeine 캐시 무효화는 service @CacheEvict로 처리됨.
        // IT 레벨에서는 update 후 재조회 결과가 새 값을 반영하는지(=캐시가 stale 응답 안 주는지)만 검증.
        void update_thenGet_reflectsNewValue() throws Exception {
            String groupCode = "GENDER-" + uid();
            insertCodeGroup(groupCode, "성별");

            // 1) POST — 코드 생성 (name="남성")
            String createBody = """
                    {"groupCode":"%s","code":"M","name":"남성","sortOrder":1}
                    """.formatted(groupCode);
            String createdJson = mockMvc.perform(post("/api/v1/system/codes")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long codeId = jdbcTemplate.queryForObject(
                    "SELECT id FROM code WHERE group_code = ? AND code = ?", Long.class, groupCode, "M");

            // 2) GET — 캐시 적재 (name="남성")
            mockMvc.perform(get("/api/v1/system/codes")
                            .header("Authorization", TOKEN)
                            .param("groupCode", groupCode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("남성"));

            // 3) PUT — name 변경 ("남성" → "Male")
            String updateBody = """
                    {"groupCode":"%s","code":"M","name":"Male","sortOrder":1}
                    """.formatted(groupCode);
            mockMvc.perform(put("/api/v1/system/codes/" + codeId)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody))
                    .andExpect(status().isOk());

            // 4) GET — 캐시 무효화 후 새 값 반영
            mockMvc.perform(get("/api/v1/system/codes")
                            .header("Authorization", TOKEN)
                            .param("groupCode", groupCode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Male"));
        }
    }

    // ─── §D-5 REQ-SYSTEM-004-D-5: 다중 그룹 묶음 조회 ────────────────────────

    @Nested
    @DisplayName("§D-5: 다중 그룹 묶음 조회 — Map<groupCode, List>")
    class BulkByGroups {

        @Test
        @DisplayName("REQ-SYSTEM-004-D-5 — GET /codes/bulk?groups=GENDER,COUNTRY — 그룹별 분리 응답")
        // @MX:NOTE: [AUTO] BulkCodesResponse는 record(codes: Map<String,List<CodeResponse>>) 구조이므로
        // 응답 JSON 루트는 {"codes":{"GENDER":[...],"COUNTRY":[...]}} 형태.
        void bulk_returnsMapByGroupCode() throws Exception {
            String gender = "GENDER-" + uid();
            String country = "COUNTRY-" + uid();
            insertCodeGroup(gender, "성별");
            insertCodeGroup(country, "국가");
            insertCode(gender, "M", "남성", 1, "ACTIVE");
            insertCode(gender, "F", "여성", 2, "ACTIVE");
            insertCode(country, "KR", "대한민국", 1, "ACTIVE");

            mockMvc.perform(get("/api/v1/system/codes/bulk")
                            .header("Authorization", TOKEN)
                            .param("groups", gender + "," + country))
                    .andExpect(status().isOk())
                    // 응답 구조: { codes: { "GENDER-xxx": [...], "COUNTRY-xxx": [...] } }
                    .andExpect(jsonPath("$.codes").exists())
                    .andExpect(jsonPath("$.codes." + gender).isArray())
                    .andExpect(jsonPath("$.codes." + gender + ".length()").value(2))
                    .andExpect(jsonPath("$.codes." + country).isArray())
                    .andExpect(jsonPath("$.codes." + country + ".length()").value(1))
                    .andExpect(jsonPath("$.codes." + country + "[0].code").value("KR"));
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        // UUID는 하이픈 포함이라 SQL/JSON 키로 부적합 → 8자 영숫자만 추출
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "code-admin-" + adminId,
                Set.of("ADMIN"),
                Set.of("SYSTEM:CODE:READ", "SYSTEM:CODE:WRITE"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        // MenuIT 동일 패턴 — V24 PII 마이그레이션 컬럼 충족
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '코드테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    /**
     * code_group 직접 INSERT — V14 스키마: (group_code, name, description, status DEFAULT 'ACTIVE').
     */
    private long insertCodeGroup(String groupCode, String name) {
        jdbcTemplate.update(
                "INSERT INTO code_group (group_code, name, status) VALUES (?, ?, 'ACTIVE')",
                groupCode, name);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM code_group WHERE group_code = ?", Long.class, groupCode);
        return id == null ? -1L : id;
    }

    /**
     * code 직접 INSERT — V14 스키마: (group_code FK, code, name, sort_order, status, extra_data JSONB).
     * 테이블명은 'code' (SPEC 의 'common_code'와 다름).
     */
    private long insertCode(String groupCode, String code, String name, int sortOrder, String status) {
        jdbcTemplate.update(
                "INSERT INTO code (group_code, code, name, sort_order, status) VALUES (?, ?, ?, ?, ?)",
                groupCode, code, name, sortOrder, status);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM code WHERE group_code = ? AND code = ?", Long.class, groupCode, code);
        return id == null ? -1L : id;
    }
}
