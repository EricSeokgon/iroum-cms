package kr.co.ircp.cms.domain.media.service;

import kr.co.ircp.cms.domain.media.dto.*;
import kr.co.ircp.cms.domain.media.entity.MediaAssetUsage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 통합 미디어 라이브러리 서비스 계약.
 * REQ-MEDIA-001 ~ REQ-MEDIA-005
 *
 * // @MX:ANCHOR: [AUTO] MediaService — 미디어 도메인 서비스 계약
 * // @MX:REASON: MediaServiceImpl, MediaController, 테스트에서 참조 (fan_in >= 3)
 */
public interface MediaService {

    /**
     * 파일 업로드 — 매직넘버 검증, EXIF 제거, 후처리 작업 등록.
     * REQ-MEDIA-001-D-5: Tika 매직넘버 + MIME 화이트리스트
     * REQ-MEDIA-004-D-4: CC_BY/CC_BY_NC인 경우 copyright_holder 필수
     */
    MediaAssetSummary upload(MultipartFile file, MediaUploadRequest req, long uploaderId, String uploaderIp);

    /** 페이지네이션 검색. REQ-MEDIA-003-D-1~4 */
    List<MediaAssetSummary> search(MediaSearchRequest req);

    long countSearch(MediaSearchRequest req);

    /** UUID로 단건 조회. REQ-MEDIA-003-D-1 */
    MediaAssetDetail findByUuid(UUID uuid);

    /** 메타데이터 수정. REQ-MEDIA-003-D (PUT) */
    MediaAssetDetail update(UUID uuid, MediaUpdateRequest req);

    /**
     * 소프트 삭제 — 활성 사용처 존재 시 MediaAssetInUseException.
     * REQ-MEDIA-004-D-2
     */
    void delete(UUID uuid);

    /**
     * HMAC-SHA256 서명 URL 생성 (TTL 15분).
     * REQ-MEDIA-004-D
     */
    MediaSignedUrl generateSignedUrl(UUID uuid, String variant);

    /** 사용처 등록. REQ-MEDIA-003-D-5 */
    void registerUsage(UUID assetUuid, String usedIn, Long referenceId, String referenceTable);

    /** 사용처 해제 (removed_at 갱신). REQ-MEDIA-004-D-2 */
    void removeUsage(UUID assetUuid, String usedIn, Long referenceId, String referenceTable);

    /** 활성 사용처 목록 조회. REQ-MEDIA-003-D-5 */
    List<MediaAssetUsage> findUsages(UUID assetUuid);

    // ─── 컬렉션 (REQ-MEDIA-005-D) ─────────────────────────────────────────────

    /** 컬렉션 목록 조회 (소유자 기준). REQ-MEDIA-005-D */
    List<MediaCollectionSummary> listCollections(long ownerId);

    MediaCollectionSummary createCollection(MediaCollectionCreateRequest req, long ownerId);

    MediaCollectionDetail getCollection(Long collectionId, long requesterId);

    void addToCollection(Long collectionId, List<UUID> assetUuids, long requesterId);

    void removeFromCollection(Long collectionId, UUID assetUuid, long requesterId);

    void deleteCollection(Long collectionId, long requesterId);
}
