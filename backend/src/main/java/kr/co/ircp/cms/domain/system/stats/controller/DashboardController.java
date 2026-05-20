package kr.co.ircp.cms.domain.system.stats.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.ircp.cms.domain.system.stats.dto.DashboardKpiResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;
import kr.co.ircp.cms.domain.system.stats.service.DashboardServiceImpl;
import kr.co.ircp.cms.domain.system.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 운영 대시보드 API 컨트롤러.
 * REQ-SYSTEM-002-D
 */
@Tag(name = "System Dashboard", description = "운영 대시보드 API")
@RestController
@RequestMapping("/api/v1/system/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardServiceImpl dashboardService;
    private final StatsService statsService;

    @Operation(summary = "대시보드 KPI 조회 (60초 캐시, X-No-Cache 우회 지원)")
    @GetMapping("/kpi")
    @PreAuthorize("hasAuthority('SYSTEM:DASHBOARD')")
    public ResponseEntity<DashboardKpiResponse> kpi(
            @RequestHeader(value = "X-No-Cache", required = false) String noCache) {
        boolean skipCache = "true".equalsIgnoreCase(noCache);
        DashboardKpiResponse response = skipCache
                ? dashboardService.getKpiFresh()
                : dashboardService.getKpi(false);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "방문 추이 조회 (7/30/90일 지원)")
    @GetMapping("/trends")
    @PreAuthorize("hasAuthority('SYSTEM:DASHBOARD')")
    public ResponseEntity<List<TrendItemResponse>> trends(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(statsService.getTrendDays(1L, days));
    }

    @Operation(summary = "Top Pages 조회")
    @GetMapping("/top-pages")
    @PreAuthorize("hasAuthority('SYSTEM:DASHBOARD')")
    public ResponseEntity<List<TopPageResponse>> topPages(
            @RequestParam(defaultValue = "7d") String period) {
        int days = period.startsWith("30") ? 30 : 7;
        return ResponseEntity.ok(statsService.getTopPages(days, 1L));
    }
}
