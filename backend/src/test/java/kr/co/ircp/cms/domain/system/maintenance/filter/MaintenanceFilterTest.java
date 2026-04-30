package kr.co.ircp.cms.domain.system.maintenance.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * MaintenanceFilter GREEN 테스트.
 * REQ-SYSTEM-005-D: 점검 중 503 차단 + ADMIN 통과 로직
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceFilter GREEN 테스트 (REQ-SYSTEM-005-D)")
class MaintenanceFilterTest {

    @Mock private MaintenanceService maintenanceService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private MaintenanceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MaintenanceFilter(maintenanceService, new ObjectMapper());
        // @Value 필드 — Spring DI 없이 직접 주입 (빈 문자열 = 화이트리스트 없음)
        ReflectionTestUtils.setField(filter, "adminIpWhitelistCsv", "");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Maintenance activeMaintenance(boolean allowAdmin) {
        return Maintenance.builder()
                .id(1L)
                .title("시스템 점검")
                .messageKo("점검 중")
                .messageEn("Maintenance")
                .startAt(Instant.now().minusSeconds(60))
                .endAt(Instant.now().plusSeconds(3600))
                .status("ACTIVE")
                .allowAdminAccess(allowAdmin)
                .build();
    }

    @Test
    @DisplayName("활성 점검 없으면 FilterChain 통과")
    void no_active_maintenance_passes_through() throws Exception {
        // given
        when(maintenanceService.findActive()).thenReturn(Optional.empty());

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("활성 점검 중 일반 요청은 503 반환")
    void active_maintenance_returns_503() throws Exception {
        // given
        when(maintenanceService.findActive()).thenReturn(Optional.of(activeMaintenance(true)));
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        // SecurityContext 비어있음 — 인증 없음
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(response).setStatus(503);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("활성 점검 중 SUPER_ADMIN은 통과")
    void active_maintenance_super_admin_passes() throws Exception {
        // given
        when(maintenanceService.findActive()).thenReturn(Optional.of(activeMaintenance(true)));
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        // SecurityContext에 SUPER_ADMIN 등록
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("allowAdminAccess=false 이면 SUPER_ADMIN도 503")
    void allow_admin_false_blocks_even_admin() throws Exception {
        // given — allowAdminAccess=false 시 IP/역할 확인 없이 즉시 503
        when(maintenanceService.findActive()).thenReturn(Optional.of(activeMaintenance(false)));
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // SecurityContext에 SUPER_ADMIN 등록
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(response).setStatus(503);
        verify(chain, never()).doFilter(request, response);
    }
}
