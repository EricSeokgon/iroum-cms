package kr.co.ircp.cms.domain.content.block.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.block.dto.SharedContentBlockRequest;
import kr.co.ircp.cms.domain.content.block.dto.SharedContentBlockResponse;
import kr.co.ircp.cms.domain.content.block.service.SharedContentBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 공유 콘텐츠 블록 REST 컨트롤러.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 — 페이지 종속 블록(content.page)과 별개의
 * 전역 재사용 블록 라이브러리. 엔드포인트 {@code /api/v1/content/blocks}.
 *
 * <p>클래스명에 Shared prefix 부여: content.page 패키지의 동명 ContentBlockController/DTO 와
 * MyBatis alias·Spring 빈 이름 충돌을 회피한다(type-aliases-package = kr.co.ircp.cms.domain).
 */
// @MX:NOTE: [AUTO] HTML 타입은 SUPER_ADMIN 전용 — 컨트롤러에서 역할 검사
@RestController
@RequestMapping("/api/v1/content/blocks")
@RequiredArgsConstructor
public class SharedContentBlockController {

    private final SharedContentBlockService blockService;

    @PostMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<SharedContentBlockResponse> create(
            @Valid @RequestBody SharedContentBlockRequest req,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        // REQ-CB-005 — HTML 타입은 SUPER_ADMIN 만 허용
        requireSuperAdminForHtml(req.blockType());
        SharedContentBlockResponse resp = blockService.create(req, userId);
        return ResponseEntity.created(URI.create("/api/v1/content/blocks/" + resp.id())).body(resp);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public List<SharedContentBlockResponse> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return blockService.findAll(status, type);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public SharedContentBlockResponse findById(@PathVariable Long id) {
        return blockService.findById(id);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public Map<String, String> preview(@PathVariable Long id) {
        return Map.of("html", blockService.preview(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public SharedContentBlockResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SharedContentBlockRequest req,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        requireSuperAdminForHtml(req.blockType());
        return blockService.update(id, req, userId);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public SharedContentBlockResponse updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return blockService.updateStatus(id, body.get("status"), userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        blockService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * REQ-CB-005 — blockType=HTML 이면서 SUPER_ADMIN 역할이 없으면 403.
     *
     * <p>역할 검사는 서비스가 아닌 컨트롤러 책임(권한 게이트). JwtPrincipal 은 역할에
     * {@code ROLE_} prefix 를 부여하므로 {@code ROLE_SUPER_ADMIN} 으로 비교한다.
     */
    private void requireSuperAdminForHtml(String blockType) {
        if (!"HTML".equals(blockType)) {
            return;
        }
        boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!isSuperAdmin) {
            throw new AccessDeniedException("HTML 블록 타입은 SUPER_ADMIN만 사용 가능합니다.");
        }
    }
}
