package kr.co.ircp.cms.domain.media.service;

import kr.co.ircp.cms.domain.media.config.MediaProperties;
import kr.co.ircp.cms.domain.media.dto.*;
import kr.co.ircp.cms.domain.media.entity.*;
import kr.co.ircp.cms.domain.media.exception.*;
import kr.co.ircp.cms.domain.media.mapper.*;
import kr.co.ircp.cms.domain.media.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * MediaServiceImpl 단위 테스트.
 * REQ-MEDIA-001 ~ REQ-MEDIA-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceTest {

    @Mock MediaAssetMapper assetMapper;
    @Mock MediaAssetUsageMapper usageMapper;
    @Mock MediaCollectionMapper collectionMapper;
    @Mock MediaCollectionItemMapper collectionItemMapper;
    @Mock MediaProcessingJobMapper jobMapper;
    @Mock MediaStorage storage;

    @InjectMocks
    MediaServiceImpl sut;

    private MediaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MediaProperties();
        properties.setBasePath("/tmp/media-test");
        properties.setSignedUrlSecret("test-secret-key");
        properties.setSignedUrlTtl(Duration.ofMinutes(15));
        properties.setAllowedMimeTypes(List.of(
                "image/jpeg", "image/png", "image/gif",
                "application/pdf", "text/plain"));
        properties.setMaxSizeMb(10);

        // MediaServiceImpl의 properties 필드를 직접 주입
        // @InjectMocks가 처리하므로 Reflection으로 주입
        try {
            var field = MediaServiceImpl.class.getDeclaredField("properties");
            field.setAccessible(true);
            field.set(sut, properties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── REQ-MEDIA-001-D-5: MIME 검증 ─────────────────────────────────────────

    @Test
    @DisplayName("허용된 MIME 타입인 경우 업로드 성공")
    void upload_allowedMimeType_success() throws IOException {
        // Arrange
        byte[] jpegMagic = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", jpegMagic);
        MediaUploadRequest req = new MediaUploadRequest(
                "테스트 이미지", null, LicenseType.INTERNAL, null, List.of("테스트"), null);

        given(storage.store(any(), anyString())).willReturn("/tmp/media-test/2026/04/uuid_test.jpg");
        given(storage.storeBytes(any(), anyString())).willReturn("/tmp/media-test/2026/04/uuid_test_small.jpg");
        willDoNothing().given(assetMapper).insert(any());
        willDoNothing().given(jobMapper).insertAll(any());

        // Act — Tika가 실제로 매직넘버를 읽으므로 바이트가 JPEG 시그니처여야 함
        // 짧은 바이트 배열은 Tika가 application/octet-stream으로 반환할 수 있음
        // 이 테스트는 서비스 로직을 검증, Tika 통합은 별도 확인
        assertThatCode(() -> sut.upload(file, req, 1L, "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("파일 크기 초과 시 IllegalArgumentException 발생")
    void upload_fileTooLarge_throwsException() {
        // Arrange — maxSizeMb=10 이므로 11MB 파일
        byte[] largeData = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", largeData);
        MediaUploadRequest req = new MediaUploadRequest(
                null, null, LicenseType.INTERNAL, null, null, null);

        // Act & Assert
        assertThatThrownBy(() -> sut.upload(file, req, 1L, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 허용치");
    }

    @Test
    @DisplayName("CC_BY 라이선스이면서 copyright_holder 누락 시 예외")
    void upload_ccByWithoutCopyrightHolder_throwsException() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello".getBytes());
        MediaUploadRequest req = new MediaUploadRequest(
                null, null, LicenseType.CC_BY, null, null, null); // copyright_holder 없음

        // Act & Assert
        assertThatThrownBy(() -> sut.upload(file, req, 1L, "127.0.0.1"))
                .isInstanceOf(LicenseMissingCopyrightException.class)
                .hasMessageContaining("CC_BY");
    }

    @Test
    @DisplayName("CC_BY_NC 라이선스이면서 copyright_holder 누락 시 예외")
    void upload_ccByNcWithoutCopyrightHolder_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "pdf".getBytes());
        MediaUploadRequest req = new MediaUploadRequest(
                null, null, LicenseType.CC_BY_NC, null, null, null);

        assertThatThrownBy(() -> sut.upload(file, req, 1L, "127.0.0.1"))
                .isInstanceOf(LicenseMissingCopyrightException.class)
                .hasMessageContaining("CC_BY_NC");
    }

    // ─── REQ-MEDIA-003-D-1: 단건 조회 ─────────────────────────────────────────

    @Test
    @DisplayName("UUID로 자산 조회 성공")
    void findByUuid_found_returnsDetail() {
        UUID uuid = UUID.randomUUID();
        MediaAsset asset = buildAsset(uuid);
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(asset));

        MediaAssetDetail detail = sut.findByUuid(uuid);

        assertThat(detail.uuid()).isEqualTo(uuid);
        assertThat(detail.type()).isEqualTo(MediaType.IMAGE);
    }

    @Test
    @DisplayName("존재하지 않는 UUID 조회 시 MediaNotFoundException")
    void findByUuid_notFound_throwsException() {
        UUID uuid = UUID.randomUUID();
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.findByUuid(uuid))
                .isInstanceOf(MediaNotFoundException.class);
    }

    // ─── REQ-MEDIA-004-D-2: 사용 중 자산 삭제 차단 ──────────────────────────────

    @Test
    @DisplayName("활성 사용처가 있는 자산 삭제 시 MediaAssetInUseException")
    void delete_assetInUse_throwsException() {
        UUID uuid = UUID.randomUUID();
        MediaAsset asset = buildAsset(uuid);
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(asset));
        given(assetMapper.countActiveUsages(asset.getId())).willReturn(2);
        given(usageMapper.findActiveByAssetId(asset.getId())).willReturn(
                List.of(MediaAssetUsage.builder()
                        .assetId(asset.getId()).usedIn("POST").referenceId(1L).referenceTable("bbs_post")
                        .build()));

        assertThatThrownBy(() -> sut.delete(uuid))
                .isInstanceOf(MediaAssetInUseException.class)
                .hasMessageContaining("사용 중인 미디어 자산");
    }

    @Test
    @DisplayName("활성 사용처가 없는 자산은 소프트 삭제 성공")
    void delete_noActiveUsages_softDeletes() {
        UUID uuid = UUID.randomUUID();
        MediaAsset asset = buildAsset(uuid);
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(asset));
        given(assetMapper.countActiveUsages(asset.getId())).willReturn(0);
        willDoNothing().given(assetMapper).softDelete(asset.getId());

        assertThatCode(() -> sut.delete(uuid)).doesNotThrowAnyException();
        then(assetMapper).should().softDelete(asset.getId());
    }

    // ─── 서명 URL (REQ-MEDIA-004-D) ──────────────────────────────────────────

    @Test
    @DisplayName("서명 URL 생성 성공 — expiresAt이 미래 시각")
    void generateSignedUrl_success_expiresInFuture() {
        UUID uuid = UUID.randomUUID();
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(buildAsset(uuid)));

        MediaSignedUrl result = sut.generateSignedUrl(uuid, "original");

        assertThat(result.signedUrl()).contains("/api/v1/media/" + uuid + "/download");
        assertThat(result.expiresAt()).isAfter(java.time.Instant.now());
    }

    // ─── 사용처 등록 (REQ-MEDIA-003-D-5) ────────────────────────────────────────

    @Test
    @DisplayName("사용처 등록 성공")
    void registerUsage_success() {
        UUID uuid = UUID.randomUUID();
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(buildAsset(uuid)));
        willDoNothing().given(usageMapper).insert(any());

        assertThatCode(() -> sut.registerUsage(uuid, "POST", 1L, "bbs_post"))
                .doesNotThrowAnyException();
        then(usageMapper).should().insert(any());
    }

    @Test
    @DisplayName("존재하지 않는 자산에 사용처 등록 시 예외")
    void registerUsage_assetNotFound_throwsException() {
        UUID uuid = UUID.randomUUID();
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.registerUsage(uuid, "POST", 1L, "bbs_post"))
                .isInstanceOf(MediaNotFoundException.class);
    }

    // ─── 컬렉션 (REQ-MEDIA-005-D) ────────────────────────────────────────────

    @Test
    @DisplayName("컬렉션 생성 성공")
    void createCollection_success() {
        MediaCollectionCreateRequest req = new MediaCollectionCreateRequest("내 앨범", "설명", false);
        willDoNothing().given(collectionMapper).insert(any());

        MediaCollectionSummary result = sut.createCollection(req, 1L);

        assertThat(result.name()).isEqualTo("내 앨범");
        assertThat(result.ownerId()).isEqualTo(1L);
        then(collectionMapper).should().insert(any());
    }

    @Test
    @DisplayName("컬렉션 삭제 성공")
    void deleteCollection_success() {
        Long collectionId = 1L;
        given(collectionMapper.findById(collectionId)).willReturn(
                Optional.of(MediaCollection.builder().id(collectionId).name("앨범").ownerId(1L).build()));
        willDoNothing().given(collectionItemMapper).deleteByCollectionId(collectionId);
        willDoNothing().given(collectionMapper).deleteById(collectionId);

        assertThatCode(() -> sut.deleteCollection(collectionId, 1L)).doesNotThrowAnyException();
        then(collectionMapper).should().deleteById(collectionId);
    }

    @Test
    @DisplayName("메타데이터 수정 성공")
    void update_success() {
        UUID uuid = UUID.randomUUID();
        MediaAsset asset = buildAsset(uuid);
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(asset));
        willDoNothing().given(assetMapper).update(any());

        MediaUpdateRequest req = new MediaUpdateRequest("새 alt_text", null, List.of("태그1"), null, null, null);
        MediaAssetDetail result = sut.update(uuid, req);

        assertThat(result.altText()).isEqualTo("새 alt_text");
    }

    @Test
    @DisplayName("수정 시 CC_BY_NC이면서 copyright_holder 누락 시 예외")
    void update_ccByNcWithoutCopyright_throwsException() {
        UUID uuid = UUID.randomUUID();
        given(assetMapper.findByUuid(uuid)).willReturn(Optional.of(buildAsset(uuid)));

        MediaUpdateRequest req = new MediaUpdateRequest(null, null, null, LicenseType.CC_BY_NC, null, null);

        assertThatThrownBy(() -> sut.update(uuid, req))
                .isInstanceOf(LicenseMissingCopyrightException.class);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private MediaAsset buildAsset(UUID uuid) {
        return MediaAsset.builder()
                .id(1L)
                .uuid(uuid)
                .type(MediaType.IMAGE)
                .originalFilename("test.jpg")
                .storedPath("/tmp/media-test/test.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(1024L)
                .checksumSha256("abc123")
                .licenseType(LicenseType.INTERNAL)
                .status(MediaStatus.READY)
                .tags(List.of())
                .thumbnailPathsJson("{}")
                .build();
    }
}
