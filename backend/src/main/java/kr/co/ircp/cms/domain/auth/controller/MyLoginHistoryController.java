package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.LoginHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.LoginHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 로그인 이력 조회 API.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 인증된 사용자가 자신의 로그인 이력을 확인한다.
 * actor.userId == user_id 보장은 {@link JwtPrincipal}을 직접 사용하여 달성한다.
 *
 * <pre>
 * GET /api/v1/me/login-history  — 본인 이력 페이징 (인증 필요)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/me/login-history")
@RequiredArgsConstructor
public class MyLoginHistoryController {

    private final LoginHistoryService service;

    /**
     * 본인 로그인 이력 목록 조회.
     *
     * <p>Controller 레이어에서 principal.userId()를 직접 사용하여
     * 타인 이력 조회 가능성을 원천 차단한다.
     *
     * @param principal JWT 인증 principal
     * @param page      페이지 번호 (0-based, 기본값 0)
     * @param size      페이지 크기 (기본값 20)
     * @return 200 PageResponse&lt;LoginHistoryEntry&gt;
     */
    @GetMapping
    public PageResponse<LoginHistoryEntry> myHistory(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.findByUserId(principal.userId(), page, size);
    }
}
