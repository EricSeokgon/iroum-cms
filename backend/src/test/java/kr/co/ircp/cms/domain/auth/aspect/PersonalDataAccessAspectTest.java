package kr.co.ircp.cms.domain.auth.aspect;

import kr.co.ircp.cms.domain.auth.annotation.PersonalDataAccess;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessPurpose;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PersonalDataAccessLogService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonalDataAccessAspect 단위 테스트.
 *
 * <p>REQ-AUTH-018-D-1 — AOP join point 직접 호출로 Aspect 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PersonalDataAccessAspect 단위 테스트")
class PersonalDataAccessAspectTest {

    @Mock
    private PersonalDataAccessLogService logService;

    private PersonalDataAccessAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new PersonalDataAccessAspect(logService);
        SecurityContextHolder.clearContext();
    }

    // ──────────────────────────────────────────────────────────────
    // afterAccess
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("afterAccess — 열람자가 타인인 경우 로그 적재")
    void afterAccess_logsWhenViewerNotSelf() throws Exception {
        // given
        setSecurityContext(1L, "SUPER_ADMIN");
        JoinPoint jp = buildJoinPoint("id", 42L);
        PersonalDataAccess annotation = buildAnnotation(
                new String[]{"email", "name"}, "BUSINESS_INQUIRY", "id", false);

        // when
        aspect.afterAccess(jp, annotation, null);

        // then
        verify(logService).record(eq(1L), eq("SUPER_ADMIN"), eq(42L), any(), eq(PersonalDataAccessPurpose.BUSINESS_INQUIRY));
    }

    @Test
    @DisplayName("afterAccess — selfAccessOnly=true이고 열람자 == 피열람자이면 로그 적재")
    void afterAccess_logsSelfAccess_whenViewerIsTarget() throws Exception {
        // given — viewer.userId() == targetUserId == 99
        setSecurityContext(99L, "USER");
        JoinPoint jp = buildJoinPoint("currentUserId", 99L);
        PersonalDataAccess annotation = buildAnnotation(
                new String[]{"email"}, "SELF_VIEW", "currentUserId", true);

        // when
        aspect.afterAccess(jp, annotation, null);

        // then
        verify(logService).record(eq(99L), eq("USER"), eq(99L), any(), eq(PersonalDataAccessPurpose.SELF_VIEW));
    }

    @Test
    @DisplayName("afterAccess — selfAccessOnly=true이고 열람자 != 피열람자이면 적재 생략")
    void afterAccess_skipsWhenSelfAccessOnly_andViewerIsNotTarget() throws Exception {
        // given — viewer=1L, target=99L → selfAccessOnly=true이므로 생략
        setSecurityContext(1L, "SUPER_ADMIN");
        JoinPoint jp = buildJoinPoint("currentUserId", 99L);
        PersonalDataAccess annotation = buildAnnotation(
                new String[]{"email"}, "SELF_VIEW", "currentUserId", true);

        // when
        aspect.afterAccess(jp, annotation, null);

        // then
        verify(logService, never()).record(anyLong(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("afterAccess — SecurityContext에 Principal 없으면 적재 생략")
    void afterAccess_handlesMissingPrincipal_gracefully() throws Exception {
        // given — 인증되지 않은 상태
        SecurityContextHolder.clearContext();
        JoinPoint jp = buildJoinPoint("id", 10L);
        PersonalDataAccess annotation = buildAnnotation(
                new String[]{"email"}, "BUSINESS_INQUIRY", "id", false);

        // when
        aspect.afterAccess(jp, annotation, null);

        // then
        verify(logService, never()).record(anyLong(), anyString(), anyLong(), any(), any());
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private void setSecurityContext(long userId, String role) {
        JwtPrincipal principal = new JwtPrincipal(userId, "testUser", Set.of(role));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Set.of());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    /**
     * 단일 파라미터 메서드를 흉내 내는 JoinPoint Mock 생성.
     *
     * <p>{@code paramName}에 맞는 SampleService 메서드를 선택해 실제 파라미터 이름이
     * 일치하도록 한다. {@code -parameters} 컴파일 옵션 필요.
     */
    private JoinPoint buildJoinPoint(String paramName, Object argValue) throws Exception {
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);

        Method method;
        if ("currentUserId".equals(paramName)) {
            method = SampleService.class.getMethod("sampleSelfMethod", long.class);
        } else {
            method = SampleService.class.getMethod("sampleMethod", long.class);
        }
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);
        when(jp.getArgs()).thenReturn(new Object[]{argValue});

        return jp;
    }

    private PersonalDataAccess buildAnnotation(String[] fields, String purpose,
                                                String targetParam, boolean selfOnly) {
        PersonalDataAccess ann = mock(PersonalDataAccess.class);
        when(ann.fields()).thenReturn(fields);
        when(ann.purpose()).thenReturn(purpose);
        when(ann.targetUserIdParam()).thenReturn(targetParam);
        when(ann.selfAccessOnly()).thenReturn(selfOnly);
        return ann;
    }

    /** 파라미터명 추출 테스트용 샘플 인터페이스 (컴파일 -parameters 필요) */
    interface SampleService {
        void sampleMethod(long id);
        void sampleSelfMethod(long currentUserId);
    }
}
