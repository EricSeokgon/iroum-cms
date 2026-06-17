package kr.co.ircp.cms.domain.point.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.point.dto.PointLedgerResponse;
import kr.co.ircp.cms.domain.point.dto.PointSummaryResponse;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 포인트 내역 조회 REST 컨트롤러.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-006.
 * <ul>
 *   <li>관리자: 사용자/이벤트/기간 필터 조회 (POINTS:READ)</li>
 *   <li>사용자: 본인 총액·내역만 조회 (인증 사용자, userId는 SecurityContext에서 도출 → 타인 데이터 접근 불가)</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] me/* 엔드포인트는 userId를 path/param으로 받지 않고 JwtPrincipal에서 도출 →
//   구조적으로 타인 데이터 조회 불가(REQ-PNT-006). 관리자 ledger는 POINTS:READ 게이트.
@RestController
@RequiredArgsConstructor
public class PointLedgerController {

    private final UserPointService userPointService;

    /** GET /api/v1/admin/points/ledger — 관리자 필터 내역 조회 (POINTS:READ). */
    @GetMapping("/api/v1/admin/points/ledger")
    @PreAuthorize("hasAuthority('POINTS:READ')")
    public ResponseEntity<PageResponse<PointLedgerResponse>> searchLedger(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                userPointService.searchLedger(userId, eventType, from, to, page, size));
    }

    /** GET /api/v1/users/me/points/summary — 본인 누적 총액 (인증 사용자). */
    @GetMapping("/api/v1/users/me/points/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PointSummaryResponse> mySummary(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(userPointService.getSummary(principal.userId()));
    }

    /** GET /api/v1/users/me/points/history — 본인 적립 내역 페이징 (인증 사용자). */
    @GetMapping("/api/v1/users/me/points/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<PointLedgerResponse>> myHistory(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userPointService.getHistory(principal.userId(), page, size));
    }
}
