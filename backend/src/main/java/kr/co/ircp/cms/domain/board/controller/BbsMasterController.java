package kr.co.ircp.cms.domain.board.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
import kr.co.ircp.cms.domain.board.dto.BbsMasterUpdateRequest;
import kr.co.ircp.cms.domain.board.service.BbsMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 게시판 마스터 REST 컨트롤러.
 * REQ-BOARD-001: 게시판 마스터 CRUD API
 */
// 경로: /api/v1/board/masters (프론트엔드 board.ts 스펙에 맞춰 변경)
@RestController
@RequestMapping("/api/v1/board/masters")
@RequiredArgsConstructor
public class BbsMasterController {

    private final BbsMasterService bbsMasterService;

    /** GET /api/v1/board/masters — 게시판 목록 조회 */
    @GetMapping
    public ResponseEntity<List<BbsMasterSummary>> listBoards() {
        return ResponseEntity.ok(bbsMasterService.listBoards());
    }

    /** GET /api/v1/board/masters/{id} — 게시판 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<BbsMasterDetail> getBoard(@PathVariable Long id) {
        return ResponseEntity.ok(bbsMasterService.getBoard(id));
    }

    /** GET /api/v1/board/masters/code/{code} — 코드로 게시판 조회 */
    @GetMapping("/code/{code}")
    public ResponseEntity<BbsMasterDetail> getBoardByCode(@PathVariable String code) {
        return ResponseEntity.ok(bbsMasterService.getBoardByCode(code));
    }

    /** POST /api/v1/board/masters — 게시판 생성 (SUPER_ADMIN 포함) */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<BbsMasterDetail> createBoard(@Valid @RequestBody BbsMasterCreateRequest request) {
        BbsMasterDetail created = bbsMasterService.createBoard(request);
        return ResponseEntity.created(URI.create("/api/v1/board/masters/" + created.id())).body(created);
    }

    /** PUT /api/v1/board/masters/{id} — 게시판 수정 (SUPER_ADMIN 포함) */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<BbsMasterDetail> updateBoard(
            @PathVariable Long id,
            @Valid @RequestBody BbsMasterUpdateRequest request
    ) {
        return ResponseEntity.ok(bbsMasterService.updateBoard(id, request));
    }

    /** DELETE /api/v1/board/masters/{id} — 게시판 삭제 (SUPER_ADMIN 포함) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> deleteBoard(@PathVariable Long id) {
        bbsMasterService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }
}
