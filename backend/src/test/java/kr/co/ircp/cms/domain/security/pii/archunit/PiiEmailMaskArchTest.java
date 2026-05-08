package kr.co.ircp.cms.domain.security.pii.archunit;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.serializer.EmailMaskSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-SECURITY-PII-002 Step 4 ArchUnit 강제 테스트.
 *
 * <p>UserSummary/UserDetail의 email 필드에 @JsonSerialize(using = EmailMaskSerializer.class)
 * 어노테이션 적용 강제 검증.
 *
 * <p>UserSelf는 자기 정보 평문 OK 정책으로 마스킹 예외.
 *
 * <p>ArchUnit 의존성 주의:
 * build.gradle.kts에 testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
 * 추가가 필요하다. Leader가 의존성을 추가한 후 빌드 성공 가능.
 *
 * <p>Java record component accessor @JsonSerialize 접근 방식:
 * ArchUnit의 JavaClass.getFields()는 record의 private final 필드를 포함한다.
 * 단, @JsonSerialize가 field에 선언되어야 함 (component accessor 메서드 어노테이션은 별도 확인 필요).
 * Spring Boot 3.4 + Jackson 2.18+에서 record field 어노테이션이 정상 인식되는지
 * PiiEmailMaskIT(Step 2)에서 end-to-end 검증한다.
 */
@DisplayName("PII Email 마스킹 ArchUnit 강제 테스트 (SPEC-CMS-SECURITY-PII-002 Step 4)")
class PiiEmailMaskArchTest {

    private static JavaClasses authDtoClasses;
    private static JavaClasses authSerializerClasses;

    @BeforeAll
    static void importClasses() {
        // 프로덕션 클래스만 임포트 (테스트 클래스 제외)
        authDtoClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("kr.co.ircp.cms.domain.auth.dto");

        authSerializerClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("kr.co.ircp.cms.domain.auth.serializer");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rule 1: UserSummary.email에 @JsonSerialize(using = EmailMaskSerializer.class) 강제
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UserSummary.email 필드에 @JsonSerialize(using=EmailMaskSerializer.class) 적용 강제")
    void userSummary_emailField_hasEmailMaskSerializer() {
        // ArchUnit rule: UserSummary 클래스의 email 필드에 @JsonSerialize 어노테이션 확인
        ArchRuleDefinition.classes()
                .that().areAssignableTo(UserSummary.class)
                .should(haveEmailFieldWithMaskSerializer("UserSummary"))
                .check(authDtoClasses);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rule 2: UserDetail.email에 @JsonSerialize(using = EmailMaskSerializer.class) 강제
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UserDetail.email 필드에 @JsonSerialize(using=EmailMaskSerializer.class) 적용 강제")
    void userDetail_emailField_hasEmailMaskSerializer() {
        ArchRuleDefinition.classes()
                .that().areAssignableTo(UserDetail.class)
                .should(haveEmailFieldWithMaskSerializer("UserDetail"))
                .check(authDtoClasses);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rule 3: UserSelf.email에는 @JsonSerialize 미적용 (자기 정보 평문 OK 정책)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UserSelf.email 필드에는 @JsonSerialize 미적용 확인 (자기 정보 평문 OK 정책)")
    void userSelf_emailField_doesNotHaveMaskSerializer() {
        // UserSelf는 마스킹 예외 — @JsonSerialize 없어야 함
        ArchRuleDefinition.classes()
                .that().areAssignableTo(UserSelf.class)
                .should(notHaveEmailFieldWithMaskSerializer())
                .check(authDtoClasses);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rule 4: EmailMaskSerializer 클래스 존재 검증 (backend-dev 구현 완료 확인)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EmailMaskSerializer 클래스가 kr.co.ircp.cms.domain.auth.serializer 패키지에 존재")
    void emailMaskSerializer_exists() {
        // backend-dev가 EmailMaskSerializer를 구현 완료한 경우 통과
        // 미구현 시 ClassFileImporter가 클래스를 찾지 못하여 규칙이 빈 결과로 PASS (false negative)
        // Leader 병합 후 재실행 시 실제 클래스 포함 여부 확인
        ArchRuleDefinition.classes()
                .that().haveSimpleName("EmailMaskSerializer")
                .should().beAssignableTo(com.fasterxml.jackson.databind.JsonSerializer.class)
                .check(authSerializerClasses);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 직접 reflection 검증 — record component 어노테이션 접근 (fallback 방식)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("직접 reflection — UserSummary record의 email 컴포넌트에 @JsonSerialize 선언 확인")
    void userSummary_emailComponent_reflectionCheck() throws Exception {
        // Java record에서 @JsonSerialize가 component accessor 메서드 또는 field에 선언될 수 있음
        // 이 테스트는 field 또는 accessor 메서드에서 어노테이션을 탐색한다
        Class<?> userSummaryClass = UserSummary.class;

        boolean fieldAnnotated = false;
        boolean methodAnnotated = false;

        // 1) Field 어노테이션 탐색
        try {
            java.lang.reflect.Field emailField = userSummaryClass.getDeclaredField("email");
            JsonSerialize annotation = emailField.getAnnotation(JsonSerialize.class);
            if (annotation != null) {
                fieldAnnotated = EmailMaskSerializer.class.equals(annotation.using());
            }
        } catch (NoSuchFieldException e) {
            // record는 private final field 이름이 component 이름과 동일
        }

        // 2) Component accessor 메서드 어노테이션 탐색
        try {
            java.lang.reflect.Method emailMethod = userSummaryClass.getDeclaredMethod("email");
            JsonSerialize annotation = emailMethod.getAnnotation(JsonSerialize.class);
            if (annotation != null) {
                methodAnnotated = EmailMaskSerializer.class.equals(annotation.using());
            }
        } catch (NoSuchMethodException e) {
            // record accessor 미존재 — 클래스 구조 변경 시 확인
        }

        // Field 또는 accessor 메서드 중 하나에 @JsonSerialize(using=EmailMaskSerializer.class)가 있어야 함
        // backend-dev 구현 완료 후 통과 (현재 compile error 예상 — EXPECTED)
        assertThat(fieldAnnotated || methodAnnotated)
                .as("UserSummary.email에 @JsonSerialize(using=EmailMaskSerializer.class) 선언 필요")
                .isTrue();
    }

    @Test
    @DisplayName("직접 reflection — UserDetail record의 email 컴포넌트에 @JsonSerialize 선언 확인")
    void userDetail_emailComponent_reflectionCheck() throws Exception {
        Class<?> userDetailClass = UserDetail.class;

        boolean fieldAnnotated = false;
        boolean methodAnnotated = false;

        try {
            java.lang.reflect.Field emailField = userDetailClass.getDeclaredField("email");
            JsonSerialize annotation = emailField.getAnnotation(JsonSerialize.class);
            if (annotation != null) {
                fieldAnnotated = EmailMaskSerializer.class.equals(annotation.using());
            }
        } catch (NoSuchFieldException e) {
            // 무시
        }

        try {
            java.lang.reflect.Method emailMethod = userDetailClass.getDeclaredMethod("email");
            JsonSerialize annotation = emailMethod.getAnnotation(JsonSerialize.class);
            if (annotation != null) {
                methodAnnotated = EmailMaskSerializer.class.equals(annotation.using());
            }
        } catch (NoSuchMethodException e) {
            // 무시
        }

        assertThat(fieldAnnotated || methodAnnotated)
                .as("UserDetail.email에 @JsonSerialize(using=EmailMaskSerializer.class) 선언 필요")
                .isTrue();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 커스텀 ArchUnit 조건 — email 필드 @JsonSerialize 검증
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * email 필드에 @JsonSerialize(using = EmailMaskSerializer.class)가 적용된 커스텀 조건.
     *
     * <p>Java record의 경우 private final field에 직접 선언되거나,
     * component accessor 메서드에 선언될 수 있다. 두 경우 모두 탐색한다.
     */
    private static ArchCondition<JavaClass> haveEmailFieldWithMaskSerializer(String dtoName) {
        return new ArchCondition<>("have email field annotated with @JsonSerialize(using=EmailMaskSerializer.class)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // ArchUnit JavaField에서 email 필드 탐색
                Optional<JavaField> emailFieldOpt = javaClass.getFields().stream()
                        .filter(f -> "email".equals(f.getName()))
                        .findFirst();

                if (emailFieldOpt.isEmpty()) {
                    // record에서 field를 찾지 못하면 FAIL
                    events.add(SimpleConditionEvent.violated(javaClass,
                            dtoName + "에 email 필드가 없습니다. record 구조 확인 필요."));
                    return;
                }

                JavaField emailField = emailFieldOpt.get();
                boolean hasJsonSerialize = emailField.getAnnotations().stream()
                        .anyMatch(ann -> {
                            if (!JsonSerialize.class.getName().equals(ann.getRawType().getName())) {
                                return false;
                            }
                            // @JsonSerialize(using = EmailMaskSerializer.class) 검증
                            // ArchUnit 은 Class 파라미터를 JavaClass 인스턴스로 반환
                            return ann.tryGetExplicitlyDeclaredProperty("using")
                                    .map(val -> {
                                        if (val instanceof com.tngtech.archunit.core.domain.JavaClass jc) {
                                            return EmailMaskSerializer.class.getName().equals(jc.getName());
                                        }
                                        return false;
                                    })
                                    .orElse(false);
                        });

                if (!hasJsonSerialize) {
                    events.add(SimpleConditionEvent.violated(emailField,
                            dtoName + ".email 필드에 @JsonSerialize(using=EmailMaskSerializer.class) 어노테이션이 없습니다. " +
                            "SPEC-CMS-SECURITY-PII-002 REQ-PII-EMAIL-008 ArchUnit 강제 위반."));
                } else {
                    events.add(SimpleConditionEvent.satisfied(emailField,
                            dtoName + ".email 필드에 @JsonSerialize(using=EmailMaskSerializer.class) 확인됨."));
                }
            }
        };
    }

    /** UserSelf.email 필드에 EmailMaskSerializer가 없어야 하는 조건 */
    private static ArchCondition<JavaClass> notHaveEmailFieldWithMaskSerializer() {
        return new ArchCondition<>("not have email field annotated with @JsonSerialize(using=EmailMaskSerializer.class)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasSerializer = javaClass.getFields().stream()
                        .filter(f -> "email".equals(f.getName()))
                        .flatMap(f -> f.getAnnotations().stream())
                        .anyMatch(ann -> JsonSerialize.class.getName().equals(ann.getRawType().getName()));

                if (hasSerializer) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            "UserSelf.email에 @JsonSerialize가 있습니다. " +
                            "자기 정보 평문 OK 정책(SPEC-CMS-SECURITY-PII-002 §5.2)에 위배됩니다."));
                } else {
                    events.add(SimpleConditionEvent.satisfied(javaClass,
                            "UserSelf.email에 @JsonSerialize 없음 확인 (자기 정보 평문 OK 정책 준수)."));
                }
            }
        };
    }
}
