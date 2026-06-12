package kr.co.ircp.cms.domain.auth.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import kr.co.ircp.cms.domain.auth.dto.MePermissionsResponse;
import kr.co.ircp.cms.domain.auth.dto.QnaNotificationPreferenceRequest;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSelfUpdateRequest;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PermissionService;
import kr.co.ircp.cms.domain.auth.service.UserService;
import kr.co.ircp.cms.domain.board.service.QnaNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 정보 조회·수정 REST 컨트롤러.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET/PUT /api/v1/me.
 * 로그인한 모든 사용자 접근 가능. 상태·역할 변경은 /api/v1/users 관리자 API 사용.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;
    private final QnaNotificationService qnaNotificationService;
    private final PermissionService permissionService;

    /**
     * 본인 정보 조회.
     *
     * <p>JWT 인증 필수. SecurityConfig에서 /api/v1/me/** 는 authenticated() 설정.
     */
    @GetMapping
    public UserSelf get(@AuthenticationPrincipal JwtPrincipal principal) {
        return userService.getMe(principal.userId());
    }

    /**
     * 본인 유효 권한 집합 조회.
     *
     * <p>SPEC-CMS-RBAC-001 REQ-RBAC-003 — GET /api/v1/me/permissions.
     * 프론트엔드 권한 판정(usePermission)의 단일 진실 소스. 인증된 모든 사용자 접근 가능.
     * 역할은 alias 포함 원본, 권한은 alias·계층 상속 반영된 유효 권한 집합(정렬).
     */
    @GetMapping("/permissions")
    public MePermissionsResponse permissions(@AuthenticationPrincipal JwtPrincipal principal) {
        List<String> roles = permissionService.findRoleCodesForUser(principal.userId())
                .stream().sorted().toList();
        List<String> permissions = permissionService.findEffectivePermissionsForUser(principal.userId())
                .stream().sorted().toList();
        return new MePermissionsResponse(roles, permissions);
    }

    /**
     * 본인 이메일·이름 수정.
     *
     * <p>상태(status)·역할(roleCodes)은 수정 불가. 관리자 전용 API 사용.
     */
    @PutMapping
    public UserSelf update(@Valid @RequestBody UserSelfUpdateRequest req,
                           @AuthenticationPrincipal JwtPrincipal principal) {
        return userService.updateMe(principal.userId(), req);
    }

    /**
     * Q&A 답변 알림 수신 설정 조회.
     *
     * <p>REQ-BOARD-014-D-4: EMAIL 채널 수신 여부 반환.
     */
    @GetMapping("/notifications/preferences")
    public ResponseEntity<Map<String, Object>> getNotificationPreferences(
            @AuthenticationPrincipal JwtPrincipal principal) {
        boolean emailEnabled = qnaNotificationService.isEmailEnabled(principal.userId());
        return ResponseEntity.ok(Map.of("qnaAnswer", Map.of("email", emailEnabled)));
    }

    /**
     * Q&A 답변 알림 옵트아웃 설정.
     *
     * <p>REQ-BOARD-014-D-4: INAPP 채널은 옵트아웃 불가, EMAIL만 허용.
     */
    @PutMapping("/notifications/preferences")
    public ResponseEntity<Void> updateNotificationPreferences(
            @Valid @RequestBody QnaNotificationPreferenceRequest req,
            @AuthenticationPrincipal JwtPrincipal principal) {
        qnaNotificationService.updateEmailOptout(principal.userId(), !req.qnaAnswer().email());
        return ResponseEntity.noContent().build();
    }
}
