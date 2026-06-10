package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.QnaAdminStatusRequest;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;
import kr.co.ircp.cms.domain.board.service.QnaAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Q&A 어드민 모더레이션 REST 컨트롤러.
 * SPEC-CMS-QNA-MODERATE-001 REQ-QNA-ADM-001~003
 */
@RestController
@RequestMapping("/api/v1/admin/qnas")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class QnaAdminController {

    private final QnaAdminService service;

    /** REQ-QNA-ADM-001: 전체 Q&A 목록 (HIDDEN 포함) */
    @GetMapping
    public ResponseEntity<PageResponse<QnaSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(service.listAll(page, size, status, keyword));
    }

    /** REQ-QNA-ADM-002: Q&A 상태 변경 */
    @PatchMapping("/{id}/status")
    public ResponseEntity<QnaSummary> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody QnaAdminStatusRequest request) {
        return ResponseEntity.ok(service.changeStatus(id, request.status()));
    }

    /** REQ-QNA-ADM-003: Q&A 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
