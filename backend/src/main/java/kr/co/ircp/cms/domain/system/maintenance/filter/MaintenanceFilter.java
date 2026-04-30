package kr.co.ircp.cms.domain.system.maintenance.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenancePublicMessage;
import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 점검 모드 필터.
 *
 * <p>REQ-SYSTEM-005-D — ACTIVE 점검 중:
 * SUPER_ADMIN / SYSADMIN 역할 또는 ADMIN_IP_WHITELIST에 포함된 IP는 통과.
 * 그 외 모든 요청: HTTP 503 + Retry-After + JSON 메시지.
 * allow_admin_access=false 시 ADMIN도 차단.
 */
// @MX:WARN: [AUTO] SecurityContextHolder 접근 — JWT 필터 이후 순서에서만 유효
// @MX:REASON: JWT 필터(JwtAuthenticationFilter)가 먼저 실행되어야 인증 정보가 채워짐
@Component
@RequiredArgsConstructor
public class MaintenanceFilter implements Filter {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_SUPER_ADMIN", "ROLE_SYSADMIN");

    @Value("${iroum.maintenance.admin-ip-whitelist:}")
    private String adminIpWhitelistCsv;

    private final MaintenanceService maintenanceService;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        Optional<Maintenance> activeMaintenance = maintenanceService.findActive();
        if (activeMaintenance.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Maintenance m = activeMaintenance.get();

        // 관리자 예외 처리
        if (isAdminAllowed(httpReq, m)) {
            chain.doFilter(request, response);
            return;
        }

        // 503 Service Unavailable 응답
        long retryAfterEpoch = m.getEndAt().getEpochSecond();
        httpRes.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        httpRes.setHeader("Retry-After", String.valueOf(retryAfterEpoch - System.currentTimeMillis() / 1000));
        httpRes.setContentType("application/json;charset=UTF-8");

        MaintenancePublicMessage msg = new MaintenancePublicMessage(
                m.getMessageKo(), m.getMessageEn(), retryAfterEpoch);
        httpRes.getWriter().write(objectMapper.writeValueAsString(msg));
    }

    private boolean isAdminAllowed(HttpServletRequest req, Maintenance m) {
        // allow_admin_access=false 시 무조건 차단
        if (Boolean.FALSE.equals(m.getAllowAdminAccess())) {
            return false;
        }

        // IP 화이트리스트 확인
        String clientIp = getClientIp(req);
        if (!adminIpWhitelistCsv.isBlank()) {
            Set<String> whitelist = Arrays.stream(adminIpWhitelistCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            if (whitelist.contains(clientIp)) return true;
        }

        // 역할 확인 (SUPER_ADMIN, SYSADMIN)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(ADMIN_ROLES::contains);
        }
        return false;
    }

    private String getClientIp(HttpServletRequest req) {
        String xForwardedFor = req.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
