package kr.co.ircp.cms.domain.media.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 통합 미디어 자산 엔티티.
 * REQ-MEDIA-001-D: 이미지·동영상·문서·오디오 단일 자산 모델
 *
 * // @MX:ANCHOR: [AUTO] MediaAsset — 미디어 도메인 핵심 엔티티, MediaService·Mapper·Controller 참조
 * // @MX:REASON: MediaAssetMapper, MediaServiceImpl, MediaController에서 참조 (fan_in >= 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    private Long id;
    private UUID uuid;
    private MediaType type;
    private String originalFilename;
    private String storedPath;
    private String publicUrl;
    private String mimeType;
    private long sizeBytes;
    private String checksumSha256;
    private Integer width;
    private Integer height;
    private Double durationSec;
    /** EXIF 메타데이터 제거 완료 여부 (이미지 자산만 의미 있음) */
    private boolean exifStripped;
    private String webpPath;
    /** {"small":"path","medium":"path","large":"path"} */
    private String thumbnailPathsJson;
    /** 접근성 대체 텍스트 (KWCAG 2.2 1.1.1) */
    private String altText;
    private String description;
    /** PostgreSQL TEXT[] 태그 배열 */
    private List<String> tags;
    private String copyrightHolder;
    private LicenseType licenseType;
    private String usageRestriction;
    private Long uploadedBy;
    /** IP 직접 저장 금지 (DAR-005), SHA-256 해시만 저장 */
    private String uploadedFromIpHash;
    private MediaStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
