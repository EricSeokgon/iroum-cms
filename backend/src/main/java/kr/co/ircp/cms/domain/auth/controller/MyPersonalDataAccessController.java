package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PersonalDataAccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 개인정보 접근 이력 조회 API.
 *
 * <p>REQ-AUTH-018-D-4 — 인증된 사용자가 자신에 대한 개인정보 열람 이력을 확인한다.
 * actor.userId == target_user_id 보장은 {@link JwtPrincipal}을 직접 사용하여 달성한다.
 * GET /api/v1/me/personal-data-access
 */
@RestController
@RequestMapping("/api/v1/me/personal-data-access")
@RequiredArgsConstructor
public class MyPersonalDataAccessController {

    private final PersonalDataAccessLogService service;

    /**
     * 본인 개인정보 접근 이력 목록 조회.
     *
     * <p>Controller 레이어에서 principal.userId()를 직접 사용하여
     * 타인 이력 조회 가능성을 원천 차단한다.
     */
    @GetMapping
    public PageResponse<PersonalDataAccessEntry> myAccess(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.findByTarget(principal.userId(), page, size);
    }
}
