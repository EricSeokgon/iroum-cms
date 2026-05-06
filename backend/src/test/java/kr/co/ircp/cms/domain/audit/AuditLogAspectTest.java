package kr.co.ircp.cms.domain.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.audit.aspect.AuditLogAspect;
import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.audit.service.AuditLogService.AuditLogRecord;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * AuditLogAspect 단위 테스트 (Step 3 REFACTOR).
 *
 * <p>AspectJProxyFactory로 실제 AOP 위빙을 적용하여 @AuditLog 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogAspectTest {

    @Mock
    AuditLogService auditLogService;

    AuditLogAspect aspect;

    /** AOP 프록시로 감싼 테스트 타겟. */
    AuditTarget proxy;

    @BeforeEach
    void setUp() {
        aspect = new AuditLogAspect(auditLogService, new ObjectMapper());

        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditTarget());
        factory.addAspect(aspect);
        proxy = factory.getProxy();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("성공 시 action과 result=SUCCESS로 record 호출")
    void around_capturesAction_onSuccess() {
        proxy.successMethod();

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(auditLogService).record(captor.capture());

        AuditLogRecord record = captor.getValue();
        assertThat(record.action()).isEqualTo("CREATE");
        assertThat(record.result()).isEqualTo("SUCCESS");
        assertThat(record.failureReason()).isNull();
    }

    @Test
    @DisplayName("예외 발생 시 result=FAILURE + failureReason 포함하고 예외 재전파")
    void around_capturesFailure_onException() {
        assertThatThrownBy(() -> proxy.failMethod())
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(auditLogService).record(captor.capture());

        AuditLogRecord record = captor.getValue();
        assertThat(record.result()).isEqualTo("FAILURE");
        assertThat(record.failureReason()).contains("IllegalStateException");
    }

    @Test
    @DisplayName("SecurityContext에 JwtPrincipal 있으면 actorId 추출")
    void around_extractsActor_fromSecurityContext() {
        JwtPrincipal principal = new JwtPrincipal(99L, "tester", Set.of("EDITOR"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Set.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        proxy.successMethod();

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(auditLogService).record(captor.capture());

        AuditLogRecord record = captor.getValue();
        assertThat(record.actorId()).isEqualTo(99L);
        assertThat(record.actorRole()).isEqualTo("EDITOR");
    }

    @Test
    @DisplayName("감사 로그 서비스 자체 실패 시 메서드 결과가 보존됨 (non-blocking)")
    void around_isNonBlocking_whenAuditFails() {
        doThrow(new RuntimeException("audit DB down"))
                .when(auditLogService).record(any());

        // 예외가 전파되지 않고 메서드 결과가 반환됨
        String result = proxy.returningMethod();
        assertThat(result).isEqualTo("ok");
    }

    // ─── 테스트용 타겟 클래스 ────────────────────────────────────────────────

    static class AuditTarget {

        @AuditLog(action = "CREATE", entityType = "Test")
        public void successMethod() {
            // 정상 실행
        }

        @AuditLog(action = "DELETE", entityType = "Test")
        public void failMethod() {
            throw new IllegalStateException("intentional failure");
        }

        @AuditLog(action = "READ", entityType = "Test")
        public String returningMethod() {
            return "ok";
        }
    }
}
