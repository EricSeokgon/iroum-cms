package kr.co.ircp.cms.domain.system.stats.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;
import kr.co.ircp.cms.domain.system.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 접속 통계 API 컨트롤러.
 * REQ-SYSTEM-002-D, REQ-SYSTEM-003-D
 */
@Tag(name = "System Stats", description = "접속 통계 API")
@RestController
@RequestMapping("/api/v1/system/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "30일 추이 조회")
    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('SYSTEM:STATS')")
    public ResponseEntity<List<TrendItemResponse>> trend() {
        return ResponseEntity.ok(statsService.getTrend30Days(1L));
    }

    @Operation(summary = "Top Pages 조회 (7d 또는 30d)")
    @GetMapping("/top-pages")
    @PreAuthorize("hasAuthority('SYSTEM:STATS')")
    public ResponseEntity<List<TopPageResponse>> topPages(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(statsService.getTopPages(days, 1L));
    }

    @Operation(summary = "수동 재집계 (날짜 범위)")
    @PostMapping("/recompute")
    @PreAuthorize("hasAuthority('SYSTEM:STATS')")
    public ResponseEntity<Map<String, String>> recompute(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        statsService.recompute(from, to, 1L);
        return ResponseEntity.ok(Map.of("message", "재집계 완료", "from", from.toString(), "to", to.toString()));
    }
}
