package kr.co.ircp.cms.domain.board.util;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * 소유권·관리자 권한 공통 검증 컴포넌트 (SUG-1 중복 제거).
 *
 * <p>SPEC-CMS-SECURITY-IDOR — PostServiceImpl / CommentServiceImpl / AttachmentServiceImpl
 * 에 중복 구현되어 있던 ensureOwnerOrAdmin + currentUserIsAdmin 을 단일 위치로 통합한다.
 *
 * <p>관리자 역할: ROLE_ADMIN, ROLE_SUPER_ADMIN, ROLE_CONTENT_ADMIN
 */
// @MX:ANCHOR: [AUTO] AuthorizationGuard — IDOR 소유권 검증 공통 게이트
// @MX:REASON: PostServiceImpl + CommentServiceImpl + AttachmentServiceImpl 에서 fan_in >= 3 으로 공용화
// @MX:SPEC: SPEC-CMS-SECURITY-IDOR
@Component
public class AuthorizationGuard {

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_CONTENT_ADMIN"
    );

    /**
     * 소유자 본인 또는 관리자인지 검증한다.
     *
     * @param ownerId     리소스 소유자 ID (nullable — null 이면 관리자만 허용)
     * @param requesterId 요청자 ID (nullable)
     * @param denyMessage 거부 시 {@link AccessDeniedException} 메시지
     * @throws AccessDeniedException 소유자도 아니고 관리자도 아닌 경우
     */
    public void ensureOwnerOrAdmin(Long ownerId, Long requesterId, String denyMessage) {
        if (requesterId != null && Objects.equals(ownerId, requesterId)) {
            return;
        }
        if (isAdmin()) {
            return;
        }
        throw new AccessDeniedException(denyMessage);
    }

    /**
     * SecurityContext 의 현재 사용자가 관리자 역할을 보유하는지 확인한다.
     */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> ADMIN_ROLES.contains(a.getAuthority()));
    }
}
