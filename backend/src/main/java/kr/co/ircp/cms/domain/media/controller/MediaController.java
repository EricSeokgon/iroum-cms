package kr.co.ircp.cms.domain.media.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.media.dto.*;
import kr.co.ircp.cms.domain.media.entity.MediaAssetUsage;
import kr.co.ircp.cms.domain.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 통합 미디어 라이브러리 REST 컨트롤러.
 * REQ-MEDIA-001 ~ REQ-MEDIA-005
 * 기본 경로: /api/v1/media
 *
 * // @MX:NOTE: [AUTO] 모든 엔드포인트는 JWT 인증 필수. 권한 검증은 Spring Security에서 처리.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    // ─── 업로드 (REQ-MEDIA-001-D) ─────────────────────────────────────────────

    /**
     * POST /api/v1/media/upload — 단건 업로드
     * REQ-MEDIA-001-D-5: 매직넘버 + MIME 검증
     * REQ-MEDIA-004-D-1: EDITOR+ 권한 필요
     */
    @PostMapping("/upload")
    public ResponseEntity<MediaAssetSummary> upload(
            @RequestParam("file") MultipartFile file,
            @Valid @ModelAttribute MediaUploadRequest req,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest httpReq
    ) {
        String uploaderIp = httpReq.getRemoteAddr();
        MediaAssetSummary result = mediaService.upload(file, req, principal.userId(), uploaderIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ─── 조회 (REQ-MEDIA-003-D) ───────────────────────────────────────────────

    /** GET /api/v1/media — 페이지네이션 목록 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @ModelAttribute MediaSearchRequest req,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        List<MediaAssetSummary> content = mediaService.search(req);
        long total = mediaService.countSearch(req);
        return ResponseEntity.ok(Map.of(
                "content", content,
                "page", req.page(),
                "size", req.size(),
                "total", total
        ));
    }

    /** GET /api/v1/media/{uuid} — 단건 상세 */
    @GetMapping("/{uuid}")
    public ResponseEntity<MediaAssetDetail> detail(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(mediaService.findByUuid(uuid));
    }

    /** GET /api/v1/media/{uuid}/url — 서명 URL 발급 */
    @GetMapping("/{uuid}/url")
    public ResponseEntity<MediaSignedUrl> signedUrl(
            @PathVariable UUID uuid,
            @RequestParam(defaultValue = "original") String variant,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(mediaService.generateSignedUrl(uuid, variant));
    }

    /** GET /api/v1/media/{uuid}/usage — 사용처 조회 */
    @GetMapping("/{uuid}/usage")
    public ResponseEntity<List<MediaAssetUsage>> usage(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(mediaService.findUsages(uuid));
    }

    // ─── 수정 / 삭제 ─────────────────────────────────────────────────────────

    /** PUT /api/v1/media/{uuid} — 메타데이터 수정 */
    @PutMapping("/{uuid}")
    public ResponseEntity<MediaAssetDetail> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody MediaUpdateRequest req,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(mediaService.update(uuid, req));
    }

    /** DELETE /api/v1/media/{uuid} — 소프트 삭제 */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        mediaService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ─── 컬렉션 (REQ-MEDIA-005-D) ────────────────────────────────────────────

    /** GET /api/v1/media/collections — 컬렉션 목록 (소유자 기준) */
    @GetMapping("/collections")
    public ResponseEntity<List<MediaCollectionSummary>> listCollections(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(mediaService.listCollections(principal.userId()));
    }

    /** POST /api/v1/media/collections — 컬렉션 생성 */
    @PostMapping("/collections")
    public ResponseEntity<MediaCollectionSummary> createCollection(
            @Valid @RequestBody MediaCollectionCreateRequest req,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        MediaCollectionSummary result = mediaService.createCollection(req, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** GET /api/v1/media/collections/{id} — 컬렉션 상세 */
    @GetMapping("/collections/{id}")
    public ResponseEntity<MediaCollectionDetail> getCollection(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(mediaService.getCollection(id, principal.userId()));
    }

    /** POST /api/v1/media/collections/{id}/items — 자산 추가 */
    @PostMapping("/collections/{id}/items")
    public ResponseEntity<Void> addToCollection(
            @PathVariable Long id,
            @RequestBody List<UUID> assetUuids,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        mediaService.addToCollection(id, assetUuids, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** DELETE /api/v1/media/collections/{id}/items/{assetUuid} — 자산 제거 */
    @DeleteMapping("/collections/{id}/items/{assetUuid}")
    public ResponseEntity<Void> removeFromCollection(
            @PathVariable Long id,
            @PathVariable UUID assetUuid,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        mediaService.removeFromCollection(id, assetUuid, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/v1/media/collections/{id} — 컬렉션 삭제 */
    @DeleteMapping("/collections/{id}")
    public ResponseEntity<Void> deleteCollection(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        mediaService.deleteCollection(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
