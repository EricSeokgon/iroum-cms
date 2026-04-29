package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PermissionChangeEntry;
import kr.co.ircp.cms.domain.auth.service.PermissionChangeHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 권한 변경 이력 REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016 §13.A — 권한 변경 이력 조회 API (관리자 전용).
 *
 * <pre>
 * GET /api/v1/audit/permission-changes               — 전체 이력 페이징
 * GET /api/v1/audit/permission-changes/users/{userId} — 사용자별 이력 페이징
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/audit/permission-changes")
@PreAuthorize("hasAuthority('AUDIT:READ')")
@RequiredArgsConstructor
public class PermissionChangeController {

    private final PermissionChangeHistoryService service;

    /**
     * 전체 권한 변경 이력 페이징 조회.
     *
     * @param page         페이지 번호 (0-based, 기본값 0)
     * @param size         페이지 크기 (기본값 20)
     * @param sort         정렬 (기본값 changedAt,desc)
     * @param targetUserId 대상 사용자 필터 (선택)
     * @param changeType   변경 유형 필터 (선택)
     * @param changedBy    수행자 필터 (선택)
     * @param from         시작 시각 필터 (선택, ISO-8601)
     * @param to           종료 시각 필터 (선택, ISO-8601)
     * @return 200 PageResponse&lt;PermissionChangeEntry&gt;
     */
    @GetMapping
    public PageResponse<PermissionChangeEntry> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "changedAt,desc") String sort,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) Long changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return service.findPage(page, size, sort, targetUserId, changeType, changedBy, from, to);
    }

    /**
     * 특정 사용자의 권한 변경 이력 페이징 조회.
     *
     * @param userId 대상 사용자 ID
     * @param page   페이지 번호 (0-based, 기본값 0)
     * @param size   페이지 크기 (기본값 20)
     * @return 200 PageResponse&lt;PermissionChangeEntry&gt;
     */
    @GetMapping("/users/{userId}")
    public PageResponse<PermissionChangeEntry> byUser(
            @PathVariable long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.findByUser(userId, page, size);
    }
}
