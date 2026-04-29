package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.LoginHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.service.LoginHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 로그인 이력 REST 컨트롤러 (관리자 전용).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 관리자 전체 로그인 이력 페이징 조회.
 *
 * <pre>
 * GET /api/v1/audit/login-history  — 전체 이력 페이징 (AUDIT:READ 권한 필요)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/audit/login-history")
@PreAuthorize("hasAuthority('AUDIT:READ')")
@RequiredArgsConstructor
public class LoginHistoryController {

    private final LoginHistoryService service;

    /**
     * 전체 로그인 이력 페이징 조회.
     *
     * @param page      페이지 번호 (0-based, 기본값 0)
     * @param size      페이지 크기 (기본값 20)
     * @param sort      정렬 (기본값 createdAt,desc)
     * @param userId    사용자 ID 필터 (선택)
     * @param username  username 부분일치 필터 (선택)
     * @param success   성공 여부 필터 (선택)
     * @param from      시작 시각 필터 (선택, ISO-8601)
     * @param to        종료 시각 필터 (선택, ISO-8601)
     * @param ipAddress IP 정확 일치 필터 (선택)
     * @return 200 PageResponse&lt;LoginHistoryEntry&gt;
     */
    @GetMapping
    public PageResponse<LoginHistoryEntry> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String ipAddress) {

        return service.findPage(page, size, sort, userId, username, success, from, to, ipAddress);
    }
}
