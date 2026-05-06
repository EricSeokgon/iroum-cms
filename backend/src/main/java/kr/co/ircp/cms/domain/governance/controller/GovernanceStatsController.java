package kr.co.ircp.cms.domain.governance.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.governance.dto.StatsRecomputeRequest;
import kr.co.ircp.cms.domain.governance.service.GovernanceStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 거버넌스 통계 조회·재계산 REST 컨트롤러.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001~004.
 */
@RestController
@RequestMapping("/api/v1/governance/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class GovernanceStatsController {

    private final GovernanceStatsService service;

    @GetMapping("/boards")
    public ResponseEntity<?> boards(
            @RequestParam(required = false) Long boardId,
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if ("monthly".equalsIgnoreCase(period)) {
            return ResponseEntity.ok(service.findBoardMonthly(boardId, from, to));
        }
        return ResponseEntity.ok(service.findBoardDaily(boardId, from, to));
    }

    @GetMapping("/contents")
    public ResponseEntity<?> contents(
            @RequestParam(required = false) Long contentId,
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if ("monthly".equalsIgnoreCase(period)) {
            return ResponseEntity.ok(service.findContentMonthly(contentId, from, to));
        }
        return ResponseEntity.ok(service.findContentDaily(contentId, from, to));
    }

    @GetMapping("/policies")
    public ResponseEntity<?> policies(
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.findPolicyStats(policyId, from, to));
    }

    @GetMapping("/safety")
    public ResponseEntity<?> safety(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.findSafetyStats(category, from, to));
    }

    @PostMapping("/recompute")
    public ResponseEntity<Map<String, Object>> recompute(@Valid @RequestBody StatsRecomputeRequest req) {
        return ResponseEntity.ok(service.recompute(req.job(), req.from(), req.to()));
    }
}
