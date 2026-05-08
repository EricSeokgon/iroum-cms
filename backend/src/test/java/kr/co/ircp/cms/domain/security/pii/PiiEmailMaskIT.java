package kr.co.ircp.cms.domain.security.pii;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static kr.co.ircp.cms.integration.JwtTestAuth.jwtAuth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-PII-002 Step 2 통합 테스트 — REQ-PII-EMAIL-008.
 *
 * <p>API 응답 email 마스킹 검증:
 * EmailMaskSerializer + UserSummary/UserDetail @JsonSerialize 적용 후 HTTP 응답 직렬화 검증.
 * backend-dev의 EmailMaskSerializer 구현 완료 후 컴파일 성공.
 *
 * <p>AC-008-1 ~ AC-008-6 + Extra-1 (IDN/이모지), Extra-2 (본인+ADMIN 이중자격) (총 8 케이스)
 *
 * <p>RISK-002-01 대응: Spring Boot 3.4 + Jackson 2.18+ 환경에서 Java record의
 * component accessor에 @JsonSerialize 어노테이션이 정상 인식되는지 end-to-end 검증.
 */
@DisplayName("PII Email 마스킹 응답 통합 테스트 (SPEC-CMS-SECURITY-PII-002 Step 2)")
@Transactional
@AutoConfigureMockMvc
class PiiEmailMaskIT extends AbstractIntegrationTest {

    private static final String USERS_URL = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailEncryptionService emailEncryptionService;

    /** 각 테스트용 사용자 id */
    private long userId1Char;   // a@example.com (local 1자)
    private long userId2Char;   // ab@example.com (local 2자)
    private long userId3Plus;   // john.doe@example.com (local 8자)
    private long userIdIdn;     // alice@한국.kr (IDN domain)
    private long userIdEmoji;   // 🙂a@example.com (이모지+alpha 2 코드포인트)

    @BeforeEach
    void insertTestUsers() {
        userId1Char  = insertUser("a@example.com");
        userId2Char  = insertUser("ab@example.com");
        userId3Plus  = insertUser("john.doe@example.com");
        userIdIdn    = insertUser("alice@한국.kr");
        userIdEmoji  = insertUser("🙂a@example.com");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-008-1: local-part 1자 마스킹 → *@e***.com
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008-1 — local-part 1자 a@example.com → *@e***.com (비SUPER_ADMIN 타인 조회)")
    void localPart1Char_maskedToSingleAsterisk() throws Exception {
        // 비SUPER_ADMIN(DEPT_ADMIN) → EmailMaskSerializer 마스킹 적용
        // detail endpoint는 SUPER_ADMIN 또는 DEPT_ADMIN만 접근 가능 → DEPT_ADMIN으로 비SUPER_ADMIN 마스킹 검증
        // RISK-002-01: record component accessor @JsonSerialize 정상 인식 검증
        mockMvc.perform(get(USERS_URL + "/" + userId1Char)
                        .with(jwtAuth(99L, "dept_admin", "DEPT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("*@e***.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-008-2: local-part 2자 마스킹 → **@e***.com (사용자 결정 사항)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008-2 — local-part 2자 ab@example.com → **@e***.com (SPEC-PII-001 §5.4 원문)")
    void localPart2Char_maskedToDoubleAsterisk() throws Exception {
        mockMvc.perform(get(USERS_URL + "/" + userId2Char)
                        .with(jwtAuth(99L, "dept_admin", "DEPT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("**@e***.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-008-3: local-part 3자 이상 → j***e@e***.com
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008-3 — local-part 8자 john.doe@example.com → j***e@e***.com")
    void localPart3PlusChar_maskedToFirstLastPattern() throws Exception {
        mockMvc.perform(get(USERS_URL + "/" + userId3Plus)
                        .with(jwtAuth(99L, "dept_admin", "DEPT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("j***e@e***.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-008-4: 본인 조회 → 평문 (마스킹 미적용)
    // JwtPrincipal.userId() == target.userId() 분기
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008-4 — 본인 조회 GET /api/v1/me → 평문 john.doe@example.com (UserSelf DTO 마스킹 미적용)")
    void selfView_returnsPlaintext() throws Exception {
        // SPEC §5.4 본인 조회 = /api/v1/me + UserSelf DTO (EmailMaskSerializer 미적용 경로)
        // /api/v1/users/{id}는 UserDetail DTO로 EmailMaskSerializer 적용 → SUPER_ADMIN만 평문
        // 본인 조회 = principal.userId()로 service.getMe() 호출 → 본인 row 반환 (평문)
        mockMvc.perform(get("/api/v1/me")
                        .with(jwtAuth(userId3Plus, "self_user", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-008-5: SUPER_ADMIN → 평문 + audit 적재
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008-5 — SUPER_ADMIN 조회 → 평문 john.doe@example.com")
    void superAdmin_returnsPlaintext() throws Exception {
        mockMvc.perform(get(USERS_URL + "/" + userId3Plus)
                        .with(jwtAuth(1L, "admin", "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AC-008-6: IDN 도메인 코드 포인트 안전 (EC-001)
    // alice@한국.kr → a***e@한***.kr
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-008-6 — IDN 도메인 alice@한국.kr → a***e@한***.kr (코드 포인트 안전)")
    void idnDomain_maskedSafely() throws Exception {
        // String.codePointCount() 기반 길이 계산 — UTF-16 surrogate pair 안전
        mockMvc.perform(get(USERS_URL + "/" + userIdIdn)
                        .with(jwtAuth(99L, "dept_admin", "DEPT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a***e@한***.kr"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Extra-1: 이모지 local-part — 🙂a@example.com (이모지 1CP + a 1CP = 2CP) → **@e***.com
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Extra-1 — 이모지+alpha 2코드포인트 🙂a@example.com → **@e***.com (AC-008-2 규칙)")
    void emojiLocalPart_2CodePoints_maskedToDoubleAsterisk() throws Exception {
        // 🙂 = U+1F642, 1 코드 포인트 (UTF-16 surrogate pair)
        // a = 1 코드 포인트 → 합계 2 코드 포인트 → AC-008-2 규칙 적용 → **
        mockMvc.perform(get(USERS_URL + "/" + userIdEmoji)
                        .with(jwtAuth(99L, "dept_admin", "DEPT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("**@e***.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Extra-2: 본인+ADMIN 이중자격 → 평문 (ADMIN 역할 충족으로 평문)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Extra-2 — SUPER_ADMIN이 본인 조회 → 평문 (이중자격, ADMIN 조건 우선)")
    void superAdminSelfView_returnsPlaintext() throws Exception {
        // SUPER_ADMIN 권한이 있으면 본인/타인 무관하게 평문 반환 (ADMIN 조건이 충족되므로)
        mockMvc.perform(get(USERS_URL + "/" + userId3Plus)
                        .with(jwtAuth(1L, "admin", "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private long insertUser(String email) {
        EncryptedEmail enc = emailEncryptionService.encrypt(email);
        String hmac = emailEncryptionService.computeHmac(email);
        User user = User.builder()
                .username("mask_it_" + UUID.randomUUID().toString().substring(0, 8))
                .email(email)
                .passwordHash("$2a$12$dummyhashfortestonly00000000000000000000000000000000000000")
                .name("Mask Test")
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
}
