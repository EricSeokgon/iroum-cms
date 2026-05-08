package kr.co.ircp.cms.domain.auth.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.UserCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.dto.UserUpdateRequest;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.UserService;
import kr.co.ircp.cms.domain.auth.validation.NoEmailWildcard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 CRUD REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET/POST/PUT/DELETE /api/v1/users.
 * 권한 검증: SUPER_ADMIN(전체) / DEPT_ADMIN(조회·잠금해제).
 *
 * <p>REQ-PII-EMAIL-007 — @Validated 활성으로 @NoEmailWildcard Bean Validation 적용.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /**
     * 사용자 목록 조회 (페이징·검색·정렬).
     *
     * <p>권한: SUPER_ADMIN(전체), DEPT_ADMIN(소속 부서·자손 부서 사용자만 조회 — Q-24).
     *
     * <p>REQ-PII-EMAIL-007 — email 파라미터는 완전일치 HMAC 검색만 허용.
     * @NoEmailWildcard 로 partial 패턴(*, %, _, @ 미포함) 입력 시 400 반환.
     * search 파라미터는 username/name partial 검색에 사용 (email 제외).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public PageResponse<UserSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @NoEmailWildcard String email,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return userService.findPage(page, size, sort, search, status, principal);
    }

    /**
     * 사용자 상세 조회.
     *
     * <p>권한: SUPER_ADMIN, DEPT_ADMIN.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public UserDetail detail(@PathVariable long id) {
        return userService.findById(id);
    }

    /**
     * 사용자 신규 생성.
     *
     * <p>권한: SUPER_ADMIN.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetail create(@Valid @RequestBody UserCreateRequest req,
                             @AuthenticationPrincipal JwtPrincipal principal) {
        return userService.create(req, principal.userId());
    }

    /**
     * 사용자 정보 수정.
     *
     * <p>권한: SUPER_ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserDetail update(@PathVariable long id,
                             @Valid @RequestBody UserUpdateRequest req,
                             @AuthenticationPrincipal JwtPrincipal principal) {
        return userService.update(id, req, principal.userId());
    }

    /**
     * 사용자 소프트 삭제.
     *
     * <p>권한: SUPER_ADMIN. HTTP 204 No Content 반환.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id,
                       @AuthenticationPrincipal JwtPrincipal principal) {
        userService.delete(id, principal.userId());
    }

    /**
     * 계정 잠금 해제.
     *
     * <p>권한: SUPER_ADMIN, DEPT_ADMIN.
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public void unlock(@PathVariable long id,
                       @AuthenticationPrincipal JwtPrincipal principal) {
        userService.unlock(id, principal.userId());
    }

    /**
     * 강제 로그아웃 (Refresh Token 전체 회수).
     *
     * <p>권한: SUPER_ADMIN.
     */
    @PostMapping("/{id}/force-logout")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void forceLogout(@PathVariable long id,
                            @AuthenticationPrincipal JwtPrincipal principal) {
        userService.forceLogout(id, principal.userId());
    }
}
