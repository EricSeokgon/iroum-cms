package kr.co.ircp.cms.domain.auth.menu;

import java.util.List;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민 메뉴 접근 제어 REST 컨트롤러.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — GET /api/v1/admin/menus/accessible.
 * 인가: SecurityConfig {@code .anyRequest().authenticated()} HTTP 레벨 보호.
 * 메소드 레벨 @PreAuthorize 미부착(인증된 모든 관리자가 자신의 접근 가능 메뉴만 조회).
 */
// @MX:NOTE: [AUTO] AdminMenuController — 현재 사용자 접근 가능 어드민 메뉴 트리 조회
// @MX:SPEC: SPEC-CMS-RBAC-001
@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    /**
     * 현재 사용자 접근 가능 어드민 메뉴 트리 조회.
     *
     * <p>사용자 유효 권한 집합으로 접근 가능한 활성 메뉴만 부모-자식 트리로 반환한다.
     */
    @GetMapping("/accessible")
    public List<AccessibleMenu> accessible(@AuthenticationPrincipal JwtPrincipal principal) {
        return adminMenuService.findAccessibleMenus(principal.userId());
    }
}
