package kr.co.ircp.cms.security.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 RUN Step 1 — ArchUnit baseline 자동 검출.
 *
 * <p>운영 {@code @PreAuthorize} 어노테이션과 HTTP 권한 매트릭스 IT 시나리오의 정합성을
 * ArchUnit 1.3.0 기반으로 자동 검증한다. 매칭 누락 시 RED → Gradle check 실패 → CI PR 차단.
 *
 * <p>본 RUN Step 1에서는 baseline 검증 3건만 활성화한다 (REQ-AAD-001/002/004):
 * <ul>
 *   <li>AC-AAD-001-1: 운영 @PreAuthorize 메소드 카운트 baseline (103) 회귀 검증</li>
 *   <li>AC-AAD-001-2: IT @DisplayName 추출 endpoint set baseline (110) 회귀 검증</li>
 *   <li>AC-AAD-002-1: 110 endpoint baseline 정확 매칭 — 누락/추가 시 RED</li>
 * </ul>
 *
 * <p>Step 2에서 권한 어휘 변경 검출(REQ-AAD-003) 추가, Step 3에서 RED 시뮬레이션 검증.
 *
 * <p>패턴 참조: {@code PiiEmailMaskArchTest} (271줄, SPEC-CMS-SECURITY-PII-002 Step 4).
 *
 * <p>D1~D4 사용자 결정 채택:
 * <ul>
 *   <li>D1: ArchUnit 1.3.0 (이미 의존성 포함, 신규 의존성 0건)</li>
 *   <li>D2: Test RED (Gradle check 통합, CI PR 차단)</li>
 *   <li>D3: AuthorizationMatrixIT + AuthorizationMatrixExpandIT 둘 다 검증 (35 endpoint)</li>
 *   <li>D4: 신규 추가 + 권한 어휘 변경 둘 다 (Step 2~3 점진 활성화)</li>
 * </ul>
 *
 * <p>baseline 갱신 절차:
 * 운영 신규 @PreAuthorize 추가 시 IT 시나리오를 추가하고 본 클래스의
 * {@link #baselineEndpoints()} + 카운트(103/110)를 동시 갱신한다. baseline 자체가 회귀 시그널이므로
 * 자동 변경 없음 — 의도적 갱신만 허용.
 *
 * <p>RED 시뮬레이션 검증 절차 (REQ-AAD-005 — 회귀 검출 능력 보장):
 * <ol>
 *   <li><b>운영 신규 @PreAuthorize 추가</b>: 운영 컨트롤러 신규 메소드에 어노테이션 1건 추가
 *       → AC-AAD-001-1 RED (카운트 103 → 104) + AssertionError 메시지에 신규 endpoint 명시</li>
 *   <li><b>IT 시나리오 1건 제거</b>: AuthorizationMatrixIT/ExpandIT의 @Test 메소드 1건 삭제
 *       → AC-AAD-001-2 RED (110 → 109) + AC-AAD-002-1 missingFromIt에 누락 endpoint 노출</li>
 *   <li><b>운영 권한 어휘 변경</b>: 기존 hasAuthority('CONTENT:WRITE') → hasAuthority('NEW:VOCAB')
 *       → AC-AAD-003-1 RED (removedFromOps에 CONTENT:WRITE + addedInOps에 NEW:VOCAB 노출)</li>
 *   <li><b>baseline 갱신 누락</b>: 신규 endpoint 추가 후 baselineEndpoints() 갱신 안 함
 *       → AC-AAD-002-1 RED (extraInIt에 신규 endpoint 노출 + ArchUnit baseline 갱신 절차 안내)</li>
 * </ol>
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001 (REQ-AAD-001/002/003/004/005)
 */
// @MX:NOTE: [AUTO] AuthorizationCoverageArchTest — 운영 @PreAuthorize ↔ IT 매트릭스 정합성 자동 검증
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001
@DisplayName("HTTP 권한 매트릭스 IT 누락 자동 검출 (SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001)")
class AuthorizationCoverageArchTest {

    private static JavaClasses operationalControllers;
    private static JavaClasses itClasses;

    /**
     * IT @DisplayName에서 endpoint(HTTP method + path)를 추출하기 위한 정규식.
     *
     * <p>예: "AC-AME-001-A1-1: POST /api/v1/content/popups — Authorization 헤더 부재 + 401"
     *  → ("POST", "/api/v1/content/popups")
     */
    private static final Pattern ENDPOINT_PATTERN =
            Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\s+(/api/v\\d+/[A-Za-z0-9_\\-/{}]+)");

    private static final String PRE_AUTHORIZE_FQN =
            "org.springframework.security.access.prepost.PreAuthorize";
    private static final String DISPLAY_NAME_FQN =
            "org.junit.jupiter.api.DisplayName";

    @BeforeAll
    static void importClasses() {
        // 운영 컨트롤러 (테스트 클래스 제외) — kr.co.ircp.cms 전체 도메인
        operationalControllers = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("kr.co.ircp.cms");

        // IT 클래스 (security 패키지 — AuthorizationMatrixIT, AuthorizationMatrixExpandIT)
        // ONLY_INCLUDE_TESTS는 src/test 컴파일 산출물만 적재
        itClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("kr.co.ircp.cms.security");
    }

    // =================================================================================
    // §A REQ-AAD-001 / REQ-AAD-002 — 운영 @PreAuthorize baseline 검증
    // =================================================================================

    /**
     * AC-AAD-001-1: 운영 @PreAuthorize 메소드 카운트 baseline 회귀 검증.
     *
     * <p>운영 @PreAuthorize 어노테이션이 추가/제거되면 카운트 변경 → RED.
     * 의도적 변경 시 본 baseline + IT 시나리오를 함께 갱신해야 함.
     */
    @Test
    @DisplayName("AC-AAD-001-1: 운영 @PreAuthorize 메소드 카운트 baseline 회귀 검증 (현재 114, 메소드 레벨만)")
    void operational_preAuthorize_baselineCount() {
        long count = operationalControllers.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(this::hasPreAuthorize)
                .count();

        // baseline: 본 RUN 시점 운영 @PreAuthorize 메소드 레벨 114건 (11개 신규 엔드포인트 추가됨)
        // 클래스 레벨 @PreAuthorize (Governance ADMIN, Retention ADMIN 등 5개 컨트롤러)는
        // 메소드 카운트에서 제외됨 — 클래스 레벨 매핑은 REQ-AAD-003 Step 2에서 별도 처리.
        // 신규 추가/제거 시 README 'HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차'를 따라
        // AuthorizationMatrixExpandIT에 시나리오를 추가하고 본 baseline을 갱신할 것.
        assertThat(count)
                .as("운영 @PreAuthorize 메소드 레벨 카운트가 baseline(114)과 다릅니다 (실제: %d). " +
                        "신규 추가 시 README 'HTTP 권한 매트릭스 IT 신규 endpoint 추가 절차'를 따라 " +
                        "AuthorizationMatrixExpandIT에 시나리오를 추가하고 본 baseline을 갱신하세요.", count)
                .isEqualTo(114L);
    }

    // =================================================================================
    // §B REQ-AAD-001 — IT @DisplayName endpoint 추출 검증
    // =================================================================================

    /**
     * AC-AAD-001-2: IT 네 클래스의 @DisplayName에서 endpoint(METHOD + path) 추출 + baseline.
     *
     * <p>AuthorizationMatrixIT(19 AC) + AuthorizationMatrixExpandIT(88 AC + smoke 1)
     * + AuthorizationMatrixExpand2IT(57 AC + smoke 1) + AuthorizationMatrixExpand3IT(106 AC + smoke 1)
     * + AuthorizationMatrixExpand4IT(~78 AC + smoke 1)에서 @DisplayName 정규식으로 unique endpoint set 추출.
     * baseline: 110 unique endpoint (AUTHZ-MATRIX-001 6 + AUTHZ-IT-EXPAND-001 29 + AUTHZ-IT-EXPAND-002 19
     * + AUTHZ-IT-EXPAND-003 34 + AUTHZ-IT-EXPAND-004 22).
     */
    @Test
    @DisplayName("AC-AAD-001-2: IT @DisplayName endpoint 추출 baseline 회귀 (110 unique endpoint)")
    void it_displayName_endpointBaselineCount() {
        Set<String> itEndpoints = extractItEndpoints();

        assertThat(itEndpoints)
                .as("IT @DisplayName에서 추출된 unique endpoint(METHOD+path) 개수가 변경되었습니다. " +
                        "AuthorizationMatrixIT, AuthorizationMatrixExpandIT, AuthorizationMatrixExpand2IT, " +
                        "AuthorizationMatrixExpand3IT 또는 AuthorizationMatrixExpand4IT에서 시나리오 추가/제거가 발생했습니다. " +
                        "본 baseline(110)을 갱신하거나 변경을 회귀 신호로 해석하세요. " +
                        "추출된 endpoint set: %s", itEndpoints)
                .hasSize(110);
    }

    /**
     * AC-AAD-002-1: 110 endpoint baseline 정확 매칭 — 누락/추가 회귀 RED.
     *
     * <p>baseline 110 endpoint (운영 @PreAuthorize 중 IT 검증 대상) ↔ IT @DisplayName 추출 set 정확 일치 검증.
     * 누락(missingFromIt) 또는 추가(extraInIt) 발생 시 RED + 어떤 endpoint가 변동되었는지 메시지 출력.
     */
    @Test
    @DisplayName("AC-AAD-002-1: 110 endpoint baseline 정확 매칭 — 누락/추가 회귀 RED")
    void it_endpointSet_matchesBaseline88() {
        Set<String> itEndpoints = extractItEndpoints();
        Set<String> baseline = baselineEndpoints();

        // baseline에 있는데 IT에 없는 endpoint (누락)
        Set<String> missingFromIt = baseline.stream()
                .filter(e -> !itEndpoints.contains(e))
                .collect(Collectors.toSet());

        // IT에 있는데 baseline에 없는 endpoint (의도적 신규 또는 baseline 갱신 필요)
        Set<String> extraInIt = itEndpoints.stream()
                .filter(e -> !baseline.contains(e))
                .collect(Collectors.toSet());

        assertThat(missingFromIt)
                .as("baseline 110 endpoint 중 IT 시나리오에 누락된 endpoint: %s. " +
                        "AuthorizationMatrixIT, AuthorizationMatrixExpandIT, AuthorizationMatrixExpand2IT, " +
                        "AuthorizationMatrixExpand3IT 또는 AuthorizationMatrixExpand4IT에 해당 시나리오가 제거되었습니다. " +
                        "회귀 검토 필요.", missingFromIt)
                .isEmpty();

        assertThat(extraInIt)
                .as("IT에는 있으나 baseline 110에 없는 신규 endpoint: %s. " +
                        "신규 시나리오 추가 시 본 ArchUnit baselineEndpoints() 메소드를 갱신하세요.", extraInIt)
                .isEmpty();
    }

    // =================================================================================
    // §C REQ-AAD-003 — 권한 어휘 변경 검출 (Step 2 추가)
    // =================================================================================

    /**
     * AC-AAD-003-1: 운영 @PreAuthorize SpEL value에서 권한 어휘 set 추출 baseline 회귀.
     *
     * <p>운영 어노테이션의 SpEL 표현(예: hasAuthority('CONTENT:WRITE'), hasRole('SUPER_ADMIN'),
     * hasAnyRole('SUPER_ADMIN','DEPT_ADMIN'), isAuthenticated()) 에서 정규식으로 권한 어휘
     * (PERMISSION:ACTION 또는 ROLE_NAME 또는 isAuthenticated)를 추출하여 unique set 생성.
     *
     * <p>baseline: AUTHZ-IT-EXPAND-001 spec.md §2.3 권한 어휘 12종 + 후속 추가 어휘.
     * 신규 어휘 등장 또는 기존 어휘 제거 시 RED — 권한 어휘 변경 회귀 검출.
     */
    @Test
    @DisplayName("AC-AAD-003-1: 운영 @PreAuthorize 권한 어휘 set baseline 회귀 검증")
    void operational_preAuthorize_authorityVocabulary_baseline() {
        Set<String> actualVocabularies = extractOperationalAuthorityVocabularies();
        Set<String> baseline = baselineAuthorityVocabularies();

        // baseline에 있는데 운영에 없는 어휘 (제거됨)
        Set<String> removedFromOps = baseline.stream()
                .filter(v -> !actualVocabularies.contains(v))
                .collect(Collectors.toSet());

        // 운영에 있는데 baseline에 없는 신규 어휘
        Set<String> addedInOps = actualVocabularies.stream()
                .filter(v -> !baseline.contains(v))
                .collect(Collectors.toSet());

        assertThat(removedFromOps)
                .as("baseline 권한 어휘 중 운영 @PreAuthorize에서 제거된 어휘: %s. " +
                        "정책 변경 시 README + IT 매트릭스 + 본 baseline을 함께 갱신하세요.", removedFromOps)
                .isEmpty();

        assertThat(addedInOps)
                .as("운영 @PreAuthorize에 신규 등장한 권한 어휘: %s. " +
                        "신규 어휘 추가 시 AuthorizationMatrixExpandIT에 해당 어휘 시나리오를 추가하고 " +
                        "본 ArchUnit baselineAuthorityVocabularies()를 갱신하세요.", addedInOps)
                .isEmpty();
    }

    // =================================================================================
    // 헬퍼 메소드
    // =================================================================================

    /** JavaMethod에 @PreAuthorize 어노테이션 보유 여부. */
    private boolean hasPreAuthorize(JavaMethod method) {
        return method.getAnnotations().stream()
                .anyMatch(a -> PRE_AUTHORIZE_FQN.equals(a.getRawType().getName()));
    }

    /**
     * IT 두 클래스(AuthorizationMatrixIT, AuthorizationMatrixExpandIT)의 모든 @Test 메소드
     * @DisplayName에서 endpoint(METHOD + 정규화된 path)를 추출하여 unique set으로 반환.
     *
     * <p>@Nested inner 클래스도 포함 (FQN에 AuthorizationMatrix 포함 + IT/Tests 패턴).
     */
    private Set<String> extractItEndpoints() {
        return itClasses.stream()
                .filter(c -> c.getName().contains("AuthorizationMatrix"))
                .flatMap(c -> c.getMethods().stream())
                .map(this::extractDisplayNameValue)
                .filter(s -> s != null && !s.isEmpty())
                .map(this::extractEndpointFromDisplayName)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** JavaMethod의 @DisplayName value 추출 (없으면 null). */
    private String extractDisplayNameValue(JavaMethod method) {
        return method.getAnnotations().stream()
                .filter(a -> DISPLAY_NAME_FQN.equals(a.getRawType().getName()))
                .findFirst()
                .flatMap(a -> a.tryGetExplicitlyDeclaredProperty("value"))
                .map(Object::toString)
                .orElse(null);
    }

    /**
     * @DisplayName 텍스트에서 endpoint(METHOD + 정규화된 path)를 추출 (매칭 없으면 빈 문자열).
     *
     * <p>경로 정규화: 숫자 ID(1, 2 ...)는 {id}로 통일하여 path variable 표기 차이를 흡수.
     *
     * <p>예: "AC-AME-001-A1-4: PUT /api/v1/content/pages/1 — ..."
     *  → "PUT /api/v1/content/pages/{id}"
     */
    private String extractEndpointFromDisplayName(String displayName) {
        Matcher m = ENDPOINT_PATTERN.matcher(displayName);
        if (!m.find()) {
            return "";
        }
        String httpMethod = m.group(1);
        String path = m.group(2);
        // 정규화 1단계: /1, /2 등 숫자 ID → /{id}
        String normalizedPath = path.replaceAll("/\\d+", "/{id}").trim();
        // 정규화 2단계: {pageId}, {blockId} 등 모든 path variable → {id} (변수명 차이 흡수)
        normalizedPath = normalizedPath.replaceAll("\\{[a-zA-Z][a-zA-Z0-9_]*\\}", "{id}");
        return httpMethod + " " + normalizedPath;
    }

    /**
     * 운영 @PreAuthorize SpEL value에서 권한 어휘를 정규식으로 추출 (REQ-AAD-003 헬퍼).
     *
     * <p>지원 패턴:
     * <ul>
     *   <li>{@code hasAuthority('PERMISSION:ACTION')} → "PERMISSION:ACTION"</li>
     *   <li>{@code hasRole('ROLE_NAME')} → "ROLE:ROLE_NAME"</li>
     *   <li>{@code hasAnyRole('R1','R2')} → "ROLE:R1", "ROLE:R2"</li>
     *   <li>{@code isAuthenticated()} → "isAuthenticated"</li>
     * </ul>
     */
    private Set<String> extractOperationalAuthorityVocabularies() {
        return operationalControllers.stream()
                .flatMap(c -> c.getMethods().stream())
                .flatMap(m -> m.getAnnotations().stream())
                .filter(a -> PRE_AUTHORIZE_FQN.equals(a.getRawType().getName()))
                .flatMap(a -> a.tryGetExplicitlyDeclaredProperty("value").stream())
                .map(Object::toString)
                .flatMap(spel -> parseSpelVocabularies(spel).stream())
                .collect(Collectors.toSet());
    }

    private static final Pattern HAS_AUTHORITY_PATTERN =
            Pattern.compile("hasAuthority\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern HAS_ROLE_PATTERN =
            Pattern.compile("hasRole\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern HAS_ANY_ROLE_PATTERN =
            Pattern.compile("hasAnyRole\\s*\\(([^)]+)\\)");
    private static final Pattern ROLE_LITERAL_PATTERN =
            Pattern.compile("['\"]([A-Z_][A-Z0-9_]*)['\"]");
    private static final Pattern IS_AUTHENTICATED_PATTERN =
            Pattern.compile("isAuthenticated\\s*\\(\\s*\\)");

    /** SpEL 표현에서 권한 어휘 set 추출 (단일 SpEL이 여러 어휘 포함 가능 — OR 조건 등). */
    private Set<String> parseSpelVocabularies(String spel) {
        Set<String> result = new java.util.HashSet<>();

        // hasAuthority('X:Y') → X:Y
        Matcher mAuth = HAS_AUTHORITY_PATTERN.matcher(spel);
        while (mAuth.find()) {
            result.add(mAuth.group(1));
        }

        // hasRole('NAME') → ROLE:NAME (ROLE 접두사로 hasAuthority와 분리)
        Matcher mRole = HAS_ROLE_PATTERN.matcher(spel);
        while (mRole.find()) {
            result.add("ROLE:" + mRole.group(1));
        }

        // hasAnyRole('A','B') → ROLE:A, ROLE:B
        Matcher mAny = HAS_ANY_ROLE_PATTERN.matcher(spel);
        while (mAny.find()) {
            String inner = mAny.group(1);
            Matcher mLit = ROLE_LITERAL_PATTERN.matcher(inner);
            while (mLit.find()) {
                result.add("ROLE:" + mLit.group(1));
            }
        }

        // isAuthenticated()
        if (IS_AUTHENTICATED_PATTERN.matcher(spel).find()) {
            result.add("isAuthenticated");
        }

        return result;
    }

    /**
     * baseline 운영 권한 어휘 set.
     *
     * <p>본 RUN 시점 운영 @PreAuthorize에서 발견된 모든 권한 어휘.
     * 신규 어휘 추가 시 IT 매트릭스 시나리오 추가 + 본 baseline 갱신 동시 진행.
     */
    private Set<String> baselineAuthorityVocabularies() {
        // 본 baseline은 운영 @PreAuthorize 어휘 전체 회귀 검출용 (~31 unique).
        // IT 매트릭스(AUTHZ-IT-EXPAND-001)는 그 중 12 어휘만 커버 (29 endpoint)
        // 나머지 ~19 어휘 IT 시나리오는 후속 SPEC AUTHZ-IT-EXPAND-002/003 대상.
        // 신규 어휘 추가 시 본 baseline + IT 시나리오 + EXPAND-002 SPEC 동시 갱신.
        return Set.of(
                // ─── Role 기반 (4종) — 운영 실측 ───────────────────────────────
                "ROLE:SUPER_ADMIN",
                "ROLE:DEPT_ADMIN",
                "ROLE:ADMIN",
                "ROLE:CONTENT_ADMIN",
                // ─── Content 영역 Authority (8종) ─────────────────────────────
                "CONTENT:WRITE",
                "CONTENT:READ",
                "PAGE:WRITE",
                "PAGE:READ",
                "PAGE:PUBLISH",
                "PAGE:ROLLBACK",
                "PAGE:HISTORY:READ",
                "SITE:WRITE",
                // ─── Block/Menu/Template Authority (5종) ──────────────────────
                "BLOCK:WRITE",
                "TEMPLATE:WRITE",
                "TEMPLATE:READ",
                "MENU:WRITE",
                "MENU:PERMISSION:WRITE",
                // ─── User/System Authority (12종) ─────────────────────────────
                "USER:READ",
                "SYSTEM:READ",
                "SYSTEM:CODE:READ",
                "SYSTEM:CODE:WRITE",
                "SYSTEM:STATS",
                "SYSTEM:DASHBOARD",
                "SYSTEM:SETTING:READ",
                "SYSTEM:SETTING:WRITE",
                "SYSTEM:MAINT:READ",
                "SYSTEM:MAINT:WRITE",
                "SYSTEM:LOG:READ",
                "SYSTEM:ADMIN",
                "AUDIT:READ",
                // ─── 인증만 요구 (1종) ─────────────────────────────────────────
                "isAuthenticated"
        );
    }

    /**
     * baseline 110 endpoint (AUTHZ-MATRIX-001 6 + AUTHZ-IT-EXPAND-001 29 + AUTHZ-IT-EXPAND-002 19
     * + AUTHZ-IT-EXPAND-003 34 + AUTHZ-IT-EXPAND-004 22).
     *
     * <p>운영 신규 @PreAuthorize 추가 시 IT 시나리오를 추가하고 본 baseline을 함께 갱신.
     * 자동 변경 없음 — 의도적 갱신만 허용 (회귀 시그널 보존).
     */
    private Set<String> baselineEndpoints() {
        return Set.of(
                // ─── AUTHZ-MATRIX-001 6 endpoint ─────────────────────────────────
                "POST /api/v1/content/banners",
                "PUT /api/v1/content/banners/{id}",
                "POST /api/v1/content/pages",
                "POST /api/v1/dashboard/cache/invalidate",
                "POST /api/v1/users",
                "GET /api/v1/governance/retention-policies",

                // ─── AUTHZ-IT-EXPAND-001 29 endpoint ──────────────────────────
                // §A.1 Content (7)
                "POST /api/v1/content/popups",
                "PUT /api/v1/content/pages/{id}",
                "POST /api/v1/content/pages/{id}/publish",
                "POST /api/v1/content/pages/{id}/schedule",
                "POST /api/v1/content/pages/{id}/retract",
                "POST /api/v1/content/templates",
                "PUT /api/v1/content/templates/{id}",
                // §A.2 Block (2) — pageId/blockId 모두 {id}로 정규화됨
                "POST /api/v1/content/pages/{id}/blocks",
                "PUT /api/v1/content/pages/{id}/blocks/{id}",
                // §A.3 Dashboard (3)
                "POST /api/v1/dashboard/widgets",
                "PUT /api/v1/dashboard/widgets/{id}",
                "GET /api/v1/system/stats/trend",
                // §A.4 Auth (4)
                "POST /api/v1/users/{id}/force-logout",
                "POST /api/v1/organizations",
                "GET /api/v1/qnas",
                "POST /api/v1/qnas",
                // §A.5 System (5) — CodeGroup 경로는 /api/v1/system/codes/groups로 변경됨 (프론트엔드 system.ts 스펙)
                "GET /api/v1/system/codes",
                "GET /api/v1/system/codes/groups",
                "POST /api/v1/system/codes",
                "PUT /api/v1/system/codes/{id}",
                "POST /api/v1/system/codes/groups",
                // §A.6 Governance (3)
                "GET /api/v1/governance/quality-rules",
                "POST /api/v1/governance/quality-rules",
                "POST /api/v1/governance/recovery-drills",
                // §A.7 BoardMenu (5)
                "POST /api/v1/content/menus",
                "PATCH /api/v1/content/menus/{id}/order",
                "DELETE /api/v1/content/menus/{id}",
                // BbsMaster 경로는 /api/v1/board/masters로 변경됨 (프론트엔드 board.ts 스펙)
                "POST /api/v1/board/masters",
                "PUT /api/v1/board/masters/{id}",

                // ─── AUTHZ-IT-EXPAND-002 19 endpoint (Phase A + B) ───────────────
                // §A.1 ContentRead 4 (CONTENT:READ/PAGE:READ/TEMPLATE:READ/ROLE:CONTENT_ADMIN)
                "GET /api/v1/content/i18n",
                "GET /api/v1/content/pages/{id}/blocks",
                "GET /api/v1/content/templates",
                "POST /api/v1/qnas/{id}/answer",
                // §A.2 PageAdvanced 2 (PAGE:ROLLBACK + PAGE:HISTORY:READ)
                "GET /api/v1/content/pages/{id}/history",
                "POST /api/v1/content/pages/{id}/rollback/{id}",
                // §A.3 SiteMenu 2 (SITE:WRITE + MENU:PERMISSION:WRITE)
                "PUT /api/v1/content/sites/{id}",
                "POST /api/v1/content/menus/{id}/permissions",
                // §A.4 UserAudit 3 (USER:READ AND AUDIT:READ + AUDIT:READ class-level)
                "GET /api/v1/audit/personal-data-access",
                "GET /api/v1/audit/permission-changes",
                "GET /api/v1/audit/login-history",
                // §A.5 Dashboard 1 (SYSTEM:DASHBOARD)
                "GET /api/v1/system/dashboard/kpi",
                // §A.6 SystemSetting 4 (SYSTEM:READ + SETTING:READ/WRITE + SYSTEM:ADMIN)
                "GET /api/v1/content/seo/redirects",
                "GET /api/v1/system/settings",
                "PUT /api/v1/system/settings/{id}",
                "POST /api/v1/content/sites",
                // §A.7 SystemOperation 3 (MAINT:READ/WRITE + LOG:READ)
                "GET /api/v1/system/maintenance",
                "POST /api/v1/system/maintenance",
                "GET /api/v1/system/access-logs",

                // ─── AUTHZ-IT-EXPAND-003 34 endpoint (Phase A + B + C) ───────────
                // §A.1 OrganizationDomain (7)
                "GET /api/v1/organizations/tree",
                "GET /api/v1/organizations",
                "GET /api/v1/organizations/{id}",
                "PUT /api/v1/organizations/{id}",
                "DELETE /api/v1/organizations/{id}",
                "GET /api/v1/organizations/{id}/history",
                "POST /api/v1/organizations/users/{id}/organization",
                // §A.2 UserDomain (5)
                "GET /api/v1/users",
                "GET /api/v1/users/{id}",
                "PUT /api/v1/users/{id}",
                "DELETE /api/v1/users/{id}",
                "POST /api/v1/users/{id}/unlock",
                // §A.3 CodeDomain (6, GET /codes/groups는 EXPAND-001 §A.5에 이미 baseline 등록됨)
                "GET /api/v1/system/codes/bulk",
                "GET /api/v1/system/codes/{id}",
                "DELETE /api/v1/system/codes/{id}",
                "GET /api/v1/system/codes/groups/{id}",
                "PUT /api/v1/system/codes/groups/{id}",
                "DELETE /api/v1/system/codes/groups/{id}",
                // §A.4 MenuMaintenance (4)
                "PATCH /api/v1/content/menus/{id}/move",
                "PATCH /api/v1/content/menus/{id}/visibility",
                "GET /api/v1/system/maintenance/{id}",
                "POST /api/v1/system/maintenance/{id}/activate",
                // §A.5 Widget (2)
                "DELETE /api/v1/dashboard/widgets/{id}",
                "POST /api/v1/dashboard/widgets/preview",
                // §A.6 BannerI18n (2)
                "DELETE /api/v1/content/banners/{id}",
                "PUT /api/v1/content/i18n",
                // §A.7 SearchPermission (3)
                "GET /api/v1/permissions",
                "GET /api/v1/search/synonyms",
                "GET /api/v1/search/stats/queries",
                // §A.8 GovernanceStats (5)
                "GET /api/v1/governance/batch-logs",
                "GET /api/v1/governance/dictionary",
                "GET /api/v1/governance/stats/policies",
                "GET /api/v1/system/stats/top-pages",
                "POST /api/v1/system/stats/recompute",

                // ─── AUTHZ-IT-EXPAND-004 22 endpoint (Phase A + B + C, Bbs/Publication/Faq/Qna/Survey + Block/Popup/Template + Role/Cache) ───
                // §A.1 BoardDomain 15 (Bbs 1 + Publication 3 + Faq 4 + Qna 3 신규 + Survey 4)
                // ※ GET /api/v1/qnas, POST /api/v1/qnas는 AUTHZ-IT-EXPAND-001 §A.4에 이미 등록되어 dedup됨
                "DELETE /api/v1/board/masters/{id}",
                "POST /api/v1/publications",
                "PUT /api/v1/publications/{id}",
                "DELETE /api/v1/publications/{id}",
                "POST /api/v1/faqs",
                "PUT /api/v1/faqs/reorder",
                "PUT /api/v1/faqs/{id}",
                "DELETE /api/v1/faqs/{id}",
                "GET /api/v1/qnas/{id}",
                "POST /api/v1/qnas/{id}/close",
                "DELETE /api/v1/qnas/{id}",
                "POST /api/v1/surveys",
                "PUT /api/v1/surveys/{id}",
                "DELETE /api/v1/surveys/{id}",
                "GET /api/v1/surveys/{id}/results",
                // §A.2 ContentDomain 5 (Block 2 + Popup 2 + Template 1, Page는 기존 baseline 100% 커버)
                "DELETE /api/v1/content/pages/{id}/blocks/{id}",
                "PATCH /api/v1/content/pages/{id}/blocks/order",
                "PUT /api/v1/content/popups/{id}",
                "DELETE /api/v1/content/popups/{id}",
                "PATCH /api/v1/content/templates/{id}/status",
                // §A.3 AuthSystemDomain 2 (Role list class-level + CacheAdmin stats)
                "GET /api/v1/roles",
                "GET /api/v1/dashboard/cache/stats"
        );
    }
}
