package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.service.PersonalDataAccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 관리자용 개인정보 접근 이력 조회 API.
 *
 * <p>REQ-AUTH-018-D-2 — AUDIT:READ + USER:READ 권한을 동시에 보유한 관리자만 접근 가능.
 * GET /api/v1/audit/personal-data-access
 */
@RestController
@RequestMapping("/api/v1/audit/personal-data-access")
@RequiredArgsConstructor
public class PersonalDataAccessController {

    private final PersonalDataAccessLogService service;

    /**
     * 개인정보 접근 이력 목록 조회 (관리자 전용).
     *
     * @param page         페이지 번호 (0-based)
     * @param size         페이지 크기
     * @param sort         정렬 (accessedAt,desc|accessedAt,asc)
     * @param targetUserId 피열람자 ID 필터 (선택)
     * @param viewerId     열람자 ID 필터 (선택)
     * @param purpose      접근 목적 필터 (선택)
     * @param from         시작 시각 필터 (ISO-8601, 선택)
     * @param to           종료 시각 필터 (ISO-8601, 선택)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT:READ') and hasAuthority('USER:READ')")
    public PageResponse<PersonalDataAccessEntry> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "accessedAt,desc") String sort,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) Long viewerId,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return service.findPage(page, size, sort, targetUserId, viewerId, purpose, from, to);
    }
}
