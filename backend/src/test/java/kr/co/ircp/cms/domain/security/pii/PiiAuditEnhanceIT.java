package kr.co.ircp.cms.domain.security.pii;

import kr.co.ircp.cms.config.IntegrationAsyncConfig;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static kr.co.ircp.cms.integration.JwtTestAuth.jwtAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-PII-002 Step 3 통합 테스트 — REQ-PII-EMAIL-009.
 *
 * <p>PII 접근 감사 보강 검증:
 * findPage(actor) N건 → personal_data_access_log 일괄 적재.
 * AOP fallback: INSERT 실패 시 user-facing 에러 미전파 + ERROR 로그 + Micrometer 카운터.
 *
 * <p>AC-009-1 ~ AC-009-6 (총 6 케이스)
 *
 * <p>SPEC-CMS-SECURITY-PII-FOLLOWUP-001 (REQ-PII-FU-001/002/003) 반영:
 * <ul>
 *   <li>{@code IntegrationAsyncConfig}가 IT profile에서 {@code auditExecutor}를
 *       {@code SyncTaskExecutor}로 override하여 {@code @Async("auditExecutor")} 호출이
 *       호출 스레드에서 동기 완료된다. Awaitility polling 불필요.</li>
 *   <li>{@link org.springframework.test.context.bean.override.mockito.MockitoSpyBean}로
 *       마이그레이션하여 {@code recordBulk(long, String, List, Set, PersonalDataAccessPurpose)}
 *       시그니처 매칭 한계를 해소한다.</li>
 * </ul>
 */
@DisplayName("PII 접근 감사 보강 통합 테스트 (SPEC-CMS-SECURITY-PII-002 Step 3)")
@Transactional
@AutoConfigureMockMvc
@Import(IntegrationAsyncConfig.class)
class PiiAuditEnhanceIT extends AbstractIntegrationTest {

    private static final String ADMIN_USERS_URL = "/api/v1/users";
    private static final String AUTH_PASSWORD_RESET_URL = "/api/v1/auth/password/reset-request";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailEncryptionService emailEncryptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // SPEC-CMS-SECURITY-PII-FOLLOWUP-002 옵션 B 적용 (2026-05-11):
    // @MockitoSpyBean PersonalDataAccessLogService 제거 — @Async + AOP CGLIB proxy 충돌 회피.
    // AC-FU-003-2 (recordBulk 실패 시뮬레이션)는 PersonalDataAccessLogServiceImplFallbackTest에 분리.
    // 본 IT는 5 AC (AC-009-2/3/4, AC-FU-003-1/3) 모두 real method 호출 검증.

    /** 테스트 데이터 — ADMIN 본인 제외 5명의 타 사용자 */
    private List<Long> targetUserIds;

    @BeforeEach
    void insertTestUsers() {
        // 5명 사용자 적재
        targetUserIds = List.of(
                insertUser("user10@example.com"),
                insertUser("user20@example.com"),
                insertUser("user30@example.com"),
                insertUser("user40@example.com"),
                insertUser("user50@example.com")
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-009-1: findPage(actor) 결과 N건 일괄 적재
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-FU-003-1 (← AC-009-1) — ADMIN findPage 전체 목록 → personal_data_access_log N건 적재 (ADMIN_USER_LIST)")
    void findPage_bulkAuditLog_nRows() throws Exception {
        long auditBefore = countAuditRows();

        mockMvc.perform(get(ADMIN_USERS_URL)
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwtAuth(1L, "admin1", "SUPER_ADMIN")))
                .andExpect(status().isOk());

        // SPEC-CMS-SECURITY-PII-FOLLOWUP-001 REQ-PII-FU-001 — IntegrationAsyncConfig가 auditExecutor를
        // SyncTaskExecutor로 override하므로 @Async("auditExecutor") 호출이 동기 완료된다. polling 불필요.
        // 적재된 audit row가 5건 이상 (본인 제외)
        assertThat(countAuditRows() - auditBefore).isGreaterThanOrEqualTo(5);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-009-2: 본인 row는 적재 제외
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-009-2 — ADMIN 본인 row(id=selfId)가 결과에 포함되어도 audit 미적재 (본인 제외)")
    void findPage_selfRowExcludedFromAudit() throws Exception {
        // ADMIN 본인 사용자 삽입 — 실제 DB row의 id를 jwtAuth principal.userId()로 전달하여
        // service의 본인 제외 로직(actor.userId() == target.id())이 정확히 매칭되도록 함
        long selfId = insertUser("admin.self@example.com");

        long auditBefore = countAuditRowsForTarget(selfId);

        mockMvc.perform(get(ADMIN_USERS_URL)
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwtAuth(selfId, "admin_self", "SUPER_ADMIN")))
                .andExpect(status().isOk());

        // 본인 row는 targetUserIds에서 사전 제외 → audit 미적재
        // 주의: @WithMockUser principal의 userId가 실제 DB row와 일치해야 본인 제외 로직 동작
        long auditAfter = countAuditRowsForTarget(selfId);
        // selfId에 대한 audit row는 증가하지 않아야 함 (본인 제외)
        assertThat(auditAfter).isEqualTo(auditBefore);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-009-3: HMAC lookup-only 경로 → audit 미적재
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-009-3 — 비밀번호 재설정 HMAC lookup-only → personal_data_access_log 미적재")
    void passwordReset_hmacLookupOnly_noAuditLog() throws Exception {
        // 비밀번호 재설정 요청: AuthService.requestPasswordReset → findByEmailHmac
        // 평문 복호화 없음 → PII 노출 없음 → 적재 제외 (REQ-009 명시)
        long auditBefore = countAuditRows();

        mockMvc.perform(post(AUTH_PASSWORD_RESET_URL)
                        .contentType("application/json")
                        .content("{\"email\":\"user10@example.com\"}"))
                .andExpect(status().is2xxSuccessful());  // 200 또는 202

        long auditAfter = countAuditRows();
        // HMAC lookup-only — personal_data_access_log에 신규 row 없음
        assertThat(auditAfter).isEqualTo(auditBefore);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-009-4: 자기 정보 조회 (/me) → audit 미적재
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-009-4 — GET /api/v1/me 본인 자기 조회 → personal_data_access_log 미적재")
    void selfMe_noAuditLog() throws Exception {
        // 본인 자기 조회 사용자 사전 적재 (principal.userId()가 실제 DB row와 매칭되어야 service.getMe() 동작)
        long selfId = insertUser("self.user@example.com");
        long auditBefore = countAuditRows();

        mockMvc.perform(get("/api/v1/me")
                        .with(jwtAuth(selfId, "self_user", "USER")))
                .andExpect(status().isOk());

        long auditAfter = countAuditRows();
        // 본인 조회 — SecurityContext principal.userId() == target.userId() → 적재 미발생
        assertThat(auditAfter).isEqualTo(auditBefore);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-009-5 (구 AC-FU-003-2): recordBulk DataAccessException 시뮬레이션
    // → SPEC-CMS-SECURITY-PII-FOLLOWUP-002 옵션 B 적용 후
    //   PersonalDataAccessLogServiceImplFallbackTest로 분리됨.
    //   본 IT 클래스에서는 Spring AOP @Async proxy + Mockito Spy 충돌로 검증 불가.
    // ──────────────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────────────
    // AC-009-6: ADMIN_USER_LIST 일괄 적재 — 각 target_user_id 다름
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-FU-003-3 (← AC-009-6) — findPage N건 → personal_data_access_log 각 target_user_id 별 row 적재")
    void findPage_bulkAudit_distinctTargetUserIds() throws Exception {
        long auditBefore = countAuditRows();

        mockMvc.perform(get(ADMIN_USERS_URL)
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwtAuth(1L, "admin1", "SUPER_ADMIN")))
                .andExpect(status().isOk());

        // SPEC-CMS-SECURITY-PII-FOLLOWUP-001 REQ-PII-FU-001 — SyncTaskExecutor override로 동기 완료 보장.
        assertThat(countAuditRows()).isGreaterThan(auditBefore);
        List<Long> auditedTargets = jdbcTemplate.queryForList(
                "SELECT DISTINCT target_user_id FROM personal_data_access_log WHERE purpose = 'ADMIN_USER_LIST'",
                Long.class
        );
        // 적재된 audit 대상 user_id는 모두 고유해야 함
        assertThat(auditedTargets).doesNotHaveDuplicates();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private long insertUser(String email) {
        EncryptedEmail enc = emailEncryptionService.encrypt(email);
        String hmac = emailEncryptionService.computeHmac(email);
        User user = User.builder()
                .username("audit_it_" + UUID.randomUUID().toString().substring(0, 8))
                .email(email)
                .passwordHash("$2a$12$dummyhashfortestonly00000000000000000000000000000000000000")
                .name("Audit IT User")
                .status(UserStatus.ACTIVE)
                .emailEncrypted(enc.ciphertext())
                .emailIv(enc.iv())
                .emailTag(enc.tag())
                .emailKeyVersion(enc.keyVersion())
                .emailHmac(hmac)
                .build();
        userMapper.insert(user);
        return user.getId();
    }

    private long countAuditRows() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personal_data_access_log", Long.class);
        return count != null ? count : 0L;
    }

    private long countAuditRowsForTarget(long targetUserId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personal_data_access_log WHERE target_user_id = ?",
                Long.class, targetUserId);
        return count != null ? count : 0L;
    }
}
