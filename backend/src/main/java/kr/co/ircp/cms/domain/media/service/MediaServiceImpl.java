package kr.co.ircp.cms.domain.media.service;

import kr.co.ircp.cms.domain.media.config.MediaProperties;
import kr.co.ircp.cms.domain.media.dto.*;
import kr.co.ircp.cms.domain.media.entity.*;
import kr.co.ircp.cms.domain.media.exception.*;
import kr.co.ircp.cms.domain.media.mapper.*;
import kr.co.ircp.cms.domain.media.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.tika.Tika;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 통합 미디어 라이브러리 서비스 구현체.
 * REQ-MEDIA-001 ~ REQ-MEDIA-005
 *
 * // @MX:ANCHOR: [AUTO] MediaServiceImpl.upload — 업로드 파이프라인 진입점
 * // @MX:REASON: MediaController, 테스트, 향후 배치 워커에서 호출 예정 (fan_in >= 3)
 * // @MX:WARN: [AUTO] AV 스캔 미도입 (Q-3 v0.2+ 후속 검토)
 * // @MX:REASON: ClamAV 미설치 환경 대응. 1차는 매직넘버+MIME+확장자 3중 방어. v0.2+ 별도 마이그레이션 후 도입.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaServiceImpl implements MediaService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int[] THUMB_WIDTHS = {150, 300, 600};
    private static final String[] THUMB_NAMES = {"small", "medium", "large"};

    private final MediaAssetMapper assetMapper;
    private final MediaAssetUsageMapper usageMapper;
    private final MediaCollectionMapper collectionMapper;
    private final MediaCollectionItemMapper collectionItemMapper;
    private final MediaProcessingJobMapper jobMapper;
    private final MediaStorage storage;
    private final MediaProperties properties;
    private final Tika tika = new Tika();

    // ─── 업로드 ───────────────────────────────────────────────────────────────

    /**
     * 단건 업로드 처리.
     * 1. 매직넘버(Tika) + MIME 화이트리스트 검증
     * 2. 파일 저장
     * 3. SHA-256 체크섬 계산
     * 4. EXIF 제거 (이미지)
     * 5. 썸네일 생성 (이미지)
     * 6. DB 저장 + 후처리 작업 등록
     */
    @Override
    @Transactional
    public MediaAssetSummary upload(MultipartFile file, MediaUploadRequest req,
                                     long uploaderId, String uploaderIp) {
        // 파일 크기 검증
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException(
                    "파일 크기가 최대 허용치(" + properties.getMaxSizeMb() + "MB)를 초과합니다.");
        }

        // 라이선스 + 저작권자 검증 (REQ-MEDIA-004-D-4)
        if (req.licenseType() != null && req.licenseType().requiresCopyrightHolder()
                && (req.copyrightHolder() == null || req.copyrightHolder().isBlank())) {
            throw new LicenseMissingCopyrightException(req.licenseType().name());
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("파일 읽기 실패", e);
        }

        // 매직넘버 기반 MIME 검증 (REQ-MEDIA-001-D-5)
        String detectedMime = tika.detect(fileBytes);
        if (!properties.getAllowedMimeTypes().contains(detectedMime)) {
            throw new InvalidMimeTypeException(detectedMime);
        }

        // SHA-256 체크섬
        String checksum = sha256Hex(fileBytes);

        // MediaType 결정
        MediaType mediaType = resolveMediaType(detectedMime);

        // 저장 경로 생성: {yyyy}/{MM}/{uuid}_{sanitized}
        String uuid = UUID.randomUUID().toString();
        LocalDate today = LocalDate.now();
        String sanitized = sanitizeFilename(file.getOriginalFilename());
        String subPath = today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + uuid + "_" + sanitized;

        String storedPath;
        try {
            storedPath = storage.store(file, subPath);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패", e);
        }

        // IP 해시 (DAR-005)
        String ipHash = uploaderIp != null ? sha256Hex(uploaderIp.getBytes(StandardCharsets.UTF_8)) : null;

        // 이미지 크기 정보
        Integer width = null, height = null;
        String thumbnailPathsJson = "{}";
        boolean exifStripped = false;

        if (mediaType == MediaType.IMAGE) {
            try {
                // EXIF 제거 (REQ-MEDIA-002-D-1)
                byte[] stripped = stripExif(fileBytes, detectedMime);
                if (stripped != null) {
                    storage.storeBytes(stripped, subPath);
                    fileBytes = stripped;
                    exifStripped = true;
                }
                // 이미지 크기
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(fileBytes));
                if (img != null) {
                    width = img.getWidth();
                    height = img.getHeight();
                    // 썸네일 생성 (REQ-MEDIA-002-D-3)
                    thumbnailPathsJson = generateThumbnails(img, subPath);
                }
            } catch (Exception e) {
                log.warn("이미지 후처리 실패 (계속 진행): {}", e.getMessage());
            }
        }

        MediaAsset asset = MediaAsset.builder()
                .uuid(UUID.fromString(uuid))
                .type(mediaType)
                .originalFilename(file.getOriginalFilename())
                .storedPath(storedPath)
                .mimeType(detectedMime)
                .sizeBytes(file.getSize())
                .checksumSha256(checksum)
                .width(width)
                .height(height)
                .exifStripped(exifStripped)
                .thumbnailPathsJson(thumbnailPathsJson)
                .altText(req.altText())
                .description(req.description())
                .tags(req.tags() != null ? req.tags() : List.of())
                .copyrightHolder(req.copyrightHolder())
                .licenseType(req.licenseType() != null ? req.licenseType() : LicenseType.INTERNAL)
                .usageRestriction(req.usageRestriction())
                .uploadedBy(uploaderId)
                .uploadedFromIpHash(ipHash)
                .status(MediaStatus.READY)
                .build();

        assetMapper.insert(asset);

        // 후처리 작업 등록 (이미지인데 아직 WebP 변환 미완 시)
        if (mediaType == MediaType.IMAGE) {
            List<MediaProcessingJob> jobs = List.of(
                    MediaProcessingJob.builder()
                            .assetId(asset.getId())
                            .jobType("WEBP_CONVERT")
                            .status(JobStatus.PENDING)
                            .build()
            );
            jobMapper.insertAll(jobs);
        }

        log.info("미디어 업로드 완료: id={}, uuid={}, type={}", asset.getId(), asset.getUuid(), mediaType);
        return MediaAssetSummary.from(asset);
    }

    // ─── 조회 ───────────────────────────────────────────────────────────────

    @Override
    public List<MediaAssetSummary> search(MediaSearchRequest req) {
        return assetMapper.search(req).stream()
                .map(MediaAssetSummary::from)
                .toList();
    }

    @Override
    public long countSearch(MediaSearchRequest req) {
        return assetMapper.countSearch(req);
    }

    @Override
    public MediaAssetDetail findByUuid(UUID uuid) {
        MediaAsset asset = assetMapper.findByUuid(uuid)
                .orElseThrow(() -> new MediaNotFoundException(uuid));
        return MediaAssetDetail.from(asset);
    }

    // ─── 수정 ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MediaAssetDetail update(UUID uuid, MediaUpdateRequest req) {
        MediaAsset asset = assetMapper.findByUuid(uuid)
                .orElseThrow(() -> new MediaNotFoundException(uuid));

        if (req.licenseType() != null && req.licenseType().requiresCopyrightHolder()
                && (req.copyrightHolder() == null || req.copyrightHolder().isBlank())) {
            throw new LicenseMissingCopyrightException(req.licenseType().name());
        }

        if (req.altText() != null) asset.setAltText(req.altText());
        if (req.description() != null) asset.setDescription(req.description());
        if (req.tags() != null) asset.setTags(req.tags());
        if (req.licenseType() != null) asset.setLicenseType(req.licenseType());
        if (req.copyrightHolder() != null) asset.setCopyrightHolder(req.copyrightHolder());
        if (req.usageRestriction() != null) asset.setUsageRestriction(req.usageRestriction());

        assetMapper.update(asset);
        return MediaAssetDetail.from(asset);
    }

    // ─── 삭제 ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(UUID uuid) {
        MediaAsset asset = assetMapper.findByUuid(uuid)
                .orElseThrow(() -> new MediaNotFoundException(uuid));

        // 활성 사용처 확인 (REQ-MEDIA-004-D-2)
        int activeUsages = assetMapper.countActiveUsages(asset.getId());
        if (activeUsages > 0) {
            List<MediaAssetUsage> usages = usageMapper.findActiveByAssetId(asset.getId());
            throw new MediaAssetInUseException(usages);
        }

        assetMapper.softDelete(asset.getId());
    }

    // ─── 서명 URL (REQ-MEDIA-004-D) ──────────────────────────────────────────

    @Override
    public MediaSignedUrl generateSignedUrl(UUID uuid, String variant) {
        MediaAsset asset = assetMapper.findByUuid(uuid)
                .orElseThrow(() -> new MediaNotFoundException(uuid));

        long ttlSeconds = properties.getSignedUrlTtl().getSeconds();
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String payload = uuid + "|" + variant + "|" + expiresAt;
        String signature = hmacSha256(payload, properties.getSignedUrlSecret());

        String signedUrl = "/api/v1/media/" + uuid + "/download"
                + "?variant=" + variant
                + "&expires=" + expiresAt
                + "&sig=" + signature;

        return new MediaSignedUrl(signedUrl, Instant.ofEpochSecond(expiresAt));
    }

    // ─── 사용처 추적 (REQ-MEDIA-003-D-5, REQ-MEDIA-004-D-2) ─────────────────

    @Override
    @Transactional
    public void registerUsage(UUID assetUuid, String usedIn, Long referenceId, String referenceTable) {
        MediaAsset asset = assetMapper.findByUuid(assetUuid)
                .orElseThrow(() -> new MediaNotFoundException(assetUuid));
        MediaAssetUsage usage = MediaAssetUsage.builder()
                .assetId(asset.getId())
                .usedIn(usedIn)
                .referenceId(referenceId)
                .referenceTable(referenceTable)
                .build();
        usageMapper.insert(usage);
    }

    @Override
    @Transactional
    public void removeUsage(UUID assetUuid, String usedIn, Long referenceId, String referenceTable) {
        MediaAsset asset = assetMapper.findByUuid(assetUuid)
                .orElseThrow(() -> new MediaNotFoundException(assetUuid));
        usageMapper.removeUsage(asset.getId(), usedIn, referenceId, referenceTable);
    }

    @Override
    public List<MediaAssetUsage> findUsages(UUID assetUuid) {
        MediaAsset asset = assetMapper.findByUuid(assetUuid)
                .orElseThrow(() -> new MediaNotFoundException(assetUuid));
        return usageMapper.findActiveByAssetId(asset.getId());
    }

    // ─── 컬렉션 (REQ-MEDIA-005-D) ────────────────────────────────────────────

    @Override
    public List<MediaCollectionSummary> listCollections(long ownerId) {
        return collectionMapper.findByOwner(ownerId, 0, 200).stream()
                .map(MediaCollectionSummary::from)
                .toList();
    }

    @Override
    @Transactional
    public MediaCollectionSummary createCollection(MediaCollectionCreateRequest req, long ownerId) {
        MediaCollection collection = MediaCollection.builder()
                .name(req.name())
                .description(req.description())
                .ownerId(ownerId)
                .isPublic(req.isPublic())
                .sortOrder(0)
                .build();
        collectionMapper.insert(collection);
        return MediaCollectionSummary.from(collection);
    }

    @Override
    public MediaCollectionDetail getCollection(Long collectionId, long requesterId) {
        MediaCollection collection = collectionMapper.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("컬렉션을 찾을 수 없습니다: " + collectionId));

        List<MediaCollectionItem> items = collectionItemMapper.findByCollectionId(collectionId);
        List<MediaAssetSummary> assetSummaries = items.stream()
                .map(item -> assetMapper.findById(item.getAssetId())
                        .map(MediaAssetSummary::from)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        return new MediaCollectionDetail(
                collection.getId(), collection.getName(), collection.getDescription(),
                collection.getOwnerId(), collection.isPublic(), collection.getSortOrder(),
                collection.getCreatedAt(), assetSummaries
        );
    }

    @Override
    @Transactional
    public void addToCollection(Long collectionId, List<UUID> assetUuids, long requesterId) {
        collectionMapper.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("컬렉션을 찾을 수 없습니다: " + collectionId));
        for (int i = 0; i < assetUuids.size(); i++) {
            MediaAsset asset = assetMapper.findByUuid(assetUuids.get(i))
                    .orElseThrow(() -> new MediaNotFoundException(assetUuids.get(0)));
            collectionItemMapper.insert(MediaCollectionItem.builder()
                    .collectionId(collectionId)
                    .assetId(asset.getId())
                    .sortOrder(i)
                    .build());
        }
    }

    @Override
    @Transactional
    public void removeFromCollection(Long collectionId, UUID assetUuid, long requesterId) {
        MediaAsset asset = assetMapper.findByUuid(assetUuid)
                .orElseThrow(() -> new MediaNotFoundException(assetUuid));
        collectionItemMapper.delete(collectionId, asset.getId());
    }

    @Override
    @Transactional
    public void deleteCollection(Long collectionId, long requesterId) {
        collectionMapper.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("컬렉션을 찾을 수 없습니다: " + collectionId));
        collectionItemMapper.deleteByCollectionId(collectionId);
        collectionMapper.deleteById(collectionId);
    }

    // ─── 고아 자산 관리 (REQ-MEDIA-004-D-3) ───────────────────────────────────

    @Override
    public List<MediaAssetSummary> findOrphans(int olderThanDays, int page, int pageSize) {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        return assetMapper.findOrphans(olderThanDays, offset, Math.max(1, pageSize)).stream()
                .map(MediaAssetSummary::from)
                .toList();
    }

    @Override
    public long countOrphans(int olderThanDays) {
        return assetMapper.countOrphans(olderThanDays);
    }

    /**
     * 고아 자산 정리. dryRun=true면 삭제 없이 대상 수만 반환.
     * 대상은 활성 사용처가 0이고 olderThanDays일 이상 경과한 자산.
     * REQ-MEDIA-004-D-3
     */
    @Override
    @Transactional
    public long cleanupOrphans(int olderThanDays, boolean dryRun) {
        // 충분히 큰 limit으로 한 번에 조회 (배치 정리 가정). 페이지네이션 불필요.
        List<MediaAsset> orphans = assetMapper.findOrphans(olderThanDays, 0, 10000);
        if (dryRun) {
            log.info("고아 자산 정리(dryRun): olderThanDays={}, 대상={}건", olderThanDays, orphans.size());
            return orphans.size();
        }
        for (MediaAsset orphan : orphans) {
            assetMapper.softDelete(orphan.getId());
        }
        log.info("고아 자산 정리 완료: olderThanDays={}, 삭제={}건", olderThanDays, orphans.size());
        return orphans.size();
    }

    // ─── 내부 유틸리티 ────────────────────────────────────────────────────────

    private MediaType resolveMediaType(String mime) {
        if (mime.startsWith("image/")) return MediaType.IMAGE;
        if (mime.startsWith("video/")) return MediaType.VIDEO;
        if (mime.startsWith("audio/")) return MediaType.AUDIO;
        return MediaType.DOCUMENT;
    }

    /**
     * EXIF 메타데이터 제거 (Apache Commons Imaging).
     * REQ-MEDIA-002-D-1: GPS·작성자·기기정보 제거
     * JPEG만 지원, 다른 포맷은 null 반환 (원본 유지)
     */
    private byte[] stripExif(byte[] fileBytes, String mime) {
        if (!"image/jpeg".equals(mime) && !"image/jpg".equals(mime)) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 빈 TiffOutputSet으로 교체하여 모든 EXIF 제거
            var cleanOutputSet = new org.apache.commons.imaging.formats.tiff.write.TiffOutputSet();
            var jpegRewriter = new org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter();
            jpegRewriter.updateExifMetadataLossy(fileBytes, out, cleanOutputSet);
            return out.toByteArray();
        } catch (Exception e) {
            log.debug("EXIF 제거 스킵 (포맷 미지원 또는 EXIF 없음): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 썸네일 생성 (imgscalr).
     * REQ-MEDIA-002-D-3: small(150px), medium(300px), large(600px)
     */
    private String generateThumbnails(BufferedImage original, String originalSubPath) {
        StringBuilder jsonBuilder = new StringBuilder("{");
        String basePath = originalSubPath.replaceAll("\\.[^.]+$", "");

        for (int i = 0; i < THUMB_WIDTHS.length; i++) {
            try {
                BufferedImage thumb = Scalr.resize(original, Scalr.Method.QUALITY, THUMB_WIDTHS[i]);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(thumb, "jpg", out);
                String thumbPath = basePath + "_" + THUMB_NAMES[i] + ".jpg";
                storage.storeBytes(out.toByteArray(), thumbPath);
                if (i > 0) jsonBuilder.append(",");
                jsonBuilder.append("\"").append(THUMB_NAMES[i]).append("\":\"")
                        .append(properties.getBasePath()).append("/").append(thumbPath).append("\"");
            } catch (Exception e) {
                log.warn("썸네일 생성 실패 ({}): {}", THUMB_NAMES[i], e.getMessage());
            }
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 환경", e);
        }
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 서명 실패", e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
