package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.QnaAnswerRequest;
import kr.co.ircp.cms.domain.board.dto.QnaCreateRequest;
import kr.co.ircp.cms.domain.board.dto.QnaDetail;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;
import kr.co.ircp.cms.domain.board.service.QnaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Q&A REST 컨트롤러.
 * REQ-BOARD-008: 질문/답변 워크플로 + 비공개 접근 제어
 */
@RestController
@RequestMapping("/api/v1/qnas")
@RequiredArgsConstructor
public class QnaController {

    private final QnaService qnaService;

    /** GET /api/v1/qnas — Q&A 목록 페이징 (인증 필수). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<QnaSummary>> listQnas(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isPrivate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication
    ) {
        boolean isAdmin = isAdmin(authentication);
        return ResponseEntity.ok(qnaService.listQnas(status, isPrivate, keyword, page, size, requesterId, isAdmin));
    }

    /** GET /api/v1/qnas/{id} — Q&A 단건 조회 (인증 필수). */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QnaDetail> getQna(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication
    ) {
        boolean isAdmin = isAdmin(authentication);
        return ResponseEntity.ok(qnaService.getQna(id, requesterId, isAdmin));
    }

    /** POST /api/v1/qnas — Q&A 질문 등록 (인증 필수). */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QnaDetail> createQna(
            @Valid @RequestBody QnaCreateRequest request,
            @AuthenticationPrincipal Long questionerId
    ) {
        QnaDetail created = qnaService.createQna(request, questionerId);
        return ResponseEntity.created(URI.create("/api/v1/qnas/" + created.id())).body(created);
    }

    /** POST /api/v1/qnas/{id}/answer — Q&A 답변 등록 (관리자/콘텐츠관리자). */
    @PostMapping("/{id}/answer")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<QnaDetail> answerQna(
            @PathVariable Long id,
            @Valid @RequestBody QnaAnswerRequest request,
            @AuthenticationPrincipal Long answererId
    ) {
        return ResponseEntity.ok(qnaService.answerQna(id, request, answererId));
    }

    /** POST /api/v1/qnas/{id}/close — Q&A 종료 (질문자 또는 관리자). */
    @PostMapping("/{id}/close")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> closeQna(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication
    ) {
        boolean isAdmin = isAdmin(authentication);
        qnaService.closeQna(id, requesterId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/v1/qnas/{id} — Q&A 삭제 (소유자 PENDING 한정 또는 관리자). */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteQna(
            @PathVariable Long id,
            @AuthenticationPrincipal Long requesterId,
            Authentication authentication
    ) {
        boolean isAdmin = isAdmin(authentication);
        qnaService.deleteQna(id, requesterId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** Authentication에서 관리자 권한 여부 추출. */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> {
                    String role = a.getAuthority();
                    return "ROLE_ADMIN".equals(role)
                            || "ROLE_SUPER_ADMIN".equals(role)
                            || "ROLE_CONTENT_ADMIN".equals(role);
                });
    }
}
