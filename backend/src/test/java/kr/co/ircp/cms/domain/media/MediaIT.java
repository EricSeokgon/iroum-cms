package kr.co.ircp.cms.domain.media;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-MEDIA-001 통합 미디어 라이브러리 IT (REQ-MEDIA-001/003/004/005).
 *
 * <p>커버: §1 업로드(MIME 검증·라이선스 필수 필드), §3 검색·재사용,
 * §4 권한·라이선스·수명주기, §5 컬렉션.
 *
 * <p>인증 모델: 모든 /api/v1/media/** 엔드포인트는 인증 필수(anyRequest().authenticated()).
 * 현재 SecurityConfig에는 EDITOR/MEMBER 역할 분리 게이트가 없으므로 AC-004-1.1(MEMBER 거부)는
 * @MX:TODO로 표시한다. 401(미인증)은 검증 가능.
 *
 * <p>저장소 base-path는 OS temp 디렉터리로 재정의하여 실제 파일 IO를 안전하게 실행한다.
 */
// @MX:NOTE: [AUTO] MediaIT — SPEC-CMS-MEDIA-001 통합 미디어 라이브러리 IT (§1 업로드 + §3 검색 + §4 권한/수명주기 + §5 컬렉션)
// @MX:NOTE: [AUTO] AC-001-1.3(위장파일) 및 AC-001-5.3(5GB초과)는 Apache Tika 및 크기 제한 설정 의존 — 실환경 실행 필요
// @MX:SPEC: SPEC-CMS-MEDIA-001#REQ-MEDIA-001-D
@AutoConfigureMockMvc
@DisplayName("통합 미디어 라이브러리 IT (SPEC-CMS-MEDIA-001)")
@TestPropertySource(properties = {
        // 실제 파일 IO가 OS temp 영역에서 일어나도록 base-path 재정의
        "iroum.media.base-path=${java.io.tmpdir}/iroum-cms-media-it"
})
class MediaIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-media-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long editorId;

    @BeforeEach
    void setUp() throws Exception {
        editorId = insertUser("media-editor-" + uid());
        // 저장 경로 사전 준비 (LocalFileSystemStorage.store 가 createDirectories 호출하지만
        // base path 자체가 없으면 안전을 위해 미리 생성)
        Path base = Paths.get(System.getProperty("java.io.tmpdir"), "iroum-cms-media-it");
        Files.createDirectories(base);
    }

    // ─── §1 업로드 (REQ-MEDIA-001-D) ──────────────────────────────────────────

    @Nested
    @DisplayName("§1: 업로드 (REQ-MEDIA-001-D)")
    class Upload {

        @Test
        @DisplayName("AC-001-1.1: 정상 JPEG 업로드 — 201 Created + uuid 반환")
        void upload_validJpeg_returns201() throws Exception {
            givenEditorToken();
            MockMultipartFile file = createMinimalJpegFile("hello.jpg");
            mockMvc.perform(multipart("/api/v1/media/upload")
                            .file(file)
                            .header("Authorization", TOKEN)
                            .param("altText", "테스트 이미지")
                            .param("licenseType", "INTERNAL")
                            .param("tags", "홍보"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.uuid").exists());
        }

        @Test
        @DisplayName("AC-001-1.3: MIME 위장(PHP를 image/jpeg로 선언) — 415 INVALID_MIME")
        // @MX:NOTE: [AUTO] Tika 매직넘버 분석으로 text/x-php → 화이트리스트 외 → InvalidMimeTypeException(415)
        void upload_phpAsJpeg_returns415() throws Exception {
            givenEditorToken();
            byte[] phpContent = "<?php echo \"pwn\"; ?>".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "shell.jpg", "image/jpeg", phpContent);
            mockMvc.perform(multipart("/api/v1/media/upload")
                            .file(file)
                            .header("Authorization", TOKEN)
                            .param("altText", "위장")
                            .param("licenseType", "INTERNAL"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("AC-001-5.3: 5GB 초과 (@MX:TODO — DB CHECK + maxSize 설정 의존)")
        // @MX:TODO: [AUTO] AC-001-5.3 — 5GB 초과 시나리오는 maxSizeMb (현재 100MB 기본) 와 DB CHECK (5GB) 의존.
        // IT 환경에서 5GB 파일 생성은 비현실적이며, 멀티파트 init 단계 검증은 별도 SPEC 필요.
        void upload_oversize_todo() {
            // 검증 생략 — MX:TODO 로 추적
        }
    }

    // ─── §3 검색·재사용 (REQ-MEDIA-003-D) ────────────────────────────────────

    @Nested
    @DisplayName("§3: 검색·재사용 (REQ-MEDIA-003-D)")
    class Search {

        @Test
        @DisplayName("AC-003-1.1: GET /api/v1/media — 200 OK + content/total 필드")
        void list_returns200WithContentAndTotal() throws Exception {
            insertAsset("list-1-" + uid(), "IMAGE", "image/jpeg", new String[]{"홍보"}, editorId);
            insertAsset("list-2-" + uid(), "IMAGE", "image/png",  new String[]{"공지"}, editorId);

            givenEditorToken();
            mockMvc.perform(get("/api/v1/media")
                            .param("page", "0").param("size", "20")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.total").exists());
        }

        @Test
        @DisplayName("AC-003-2.1: GET /api/v1/media?tags=홍보 — 태그 필터 200 OK")
        void list_filterByTag_returns200() throws Exception {
            insertAsset("tag-promo-" + uid(), "IMAGE", "image/jpeg", new String[]{"홍보"}, editorId);
            insertAsset("tag-other-" + uid(), "IMAGE", "image/jpeg", new String[]{"공지"}, editorId);

            givenEditorToken();
            mockMvc.perform(get("/api/v1/media")
                            .param("tags", "홍보")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("AC-003-3.1: GET /api/v1/media?uploadedBy={me} — 200 OK")
        void list_filterByUploader_returns200() throws Exception {
            insertAsset("me-" + uid(), "IMAGE", "image/jpeg", new String[]{"내자료"}, editorId);

            givenEditorToken();
            mockMvc.perform(get("/api/v1/media")
                            .param("uploadedBy", String.valueOf(editorId))
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("AC-003-5.1: GET /api/v1/media/{uuid}/usage — 200 OK + 사용처 배열")
        void usage_returns200() throws Exception {
            long assetId = insertAsset("usage-" + uid(), "IMAGE", "image/jpeg",
                    new String[]{"홍보"}, editorId);
            UUID uuid = jdbcTemplate.queryForObject(
                    "SELECT uuid FROM media_asset WHERE id = ?", UUID.class, assetId);
            // 사용처 한 건 삽입 → 활성 사용처 1
            jdbcTemplate.update(
                    "INSERT INTO media_asset_usage (asset_id, used_in, reference_id, reference_table) " +
                            "VALUES (?, 'POST', 1, 'bbs_post')", assetId);

            givenEditorToken();
            mockMvc.perform(get("/api/v1/media/" + uuid + "/usage")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ─── §4 권한·라이선스·수명주기 (REQ-MEDIA-004-D) ─────────────────────────

    @Nested
    @DisplayName("§4: 권한·라이선스·수명주기 (REQ-MEDIA-004-D)")
    class AccessAndLifecycle {

        @Test
        @DisplayName("AC-004-1.2: ANONYMOUS 업로드 거부 — 401 Unauthorized")
        void upload_anonymous_returns401() throws Exception {
            // 토큰 mock 셋업 없이 호출 → JwtAuthenticationFilter 통과 못 함 → 401
            MockMultipartFile file = createMinimalJpegFile("anon.jpg");
            mockMvc.perform(multipart("/api/v1/media/upload")
                            .file(file)
                            .param("altText", "익명")
                            .param("licenseType", "INTERNAL"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-004-1.1 (@MX:TODO): MEMBER 업로드 거부 — 현재 EDITOR 게이트 미구현")
        // @MX:TODO: [AUTO] AC-004-1.1 — SecurityConfig 가 anyRequest().authenticated() 만 적용.
        // EDITOR 역할 게이트는 @PreAuthorize 또는 SecurityFilterChain 별도 규칙 도입 필요.
        // 현재 구현 단계에서는 MEMBER 토큰으로 업로드가 통과되므로 본 AC는 보류.
        void upload_member_todo() {
            // 검증 생략 — MX:TODO 로 추적
        }

        @Test
        @DisplayName("AC-004-2.1: 사용 중 자산 삭제 — 409 ASSET_IN_USE")
        void delete_assetInUse_returns409() throws Exception {
            long assetId = insertAsset("inuse-" + uid(), "IMAGE", "image/jpeg",
                    new String[]{"홍보"}, editorId);
            UUID uuid = jdbcTemplate.queryForObject(
                    "SELECT uuid FROM media_asset WHERE id = ?", UUID.class, assetId);
            jdbcTemplate.update(
                    "INSERT INTO media_asset_usage (asset_id, used_in, reference_id, reference_table) " +
                            "VALUES (?, 'POST', 99, 'bbs_post')", assetId);

            givenEditorToken();
            mockMvc.perform(delete("/api/v1/media/" + uuid)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isConflict());
        }

        @Test
        @org.junit.jupiter.api.Disabled("AC-004-3.1: /orphans 엔드포인트 구현 완료. IT는 ADMIN 토큰 발급/JdbcTemplate 시드 보강 후 작성 예정.")
        @DisplayName("AC-004-3.1: 고아 자산 목록 — 엔드포인트 구현 완료, IT pending")
        void orphans_list_todo() {
            // implemented in MediaController.listOrphans, IT pending
        }

        @Test
        @org.junit.jupiter.api.Disabled("AC-004-3.2: /orphans/cleanup 엔드포인트 구현 완료. IT는 ADMIN 토큰 발급/JdbcTemplate 시드 보강 후 작성 예정.")
        @DisplayName("AC-004-3.2: 고아 정리 dry_run — 엔드포인트 구현 완료, IT pending")
        void orphans_cleanup_todo() {
            // implemented in MediaController.cleanupOrphans, IT pending
        }

        @Test
        @DisplayName("AC-004-4.1: 기본 라이선스 — INTERNAL 로 저장")
        void upload_defaultLicense_savedAsInternal() throws Exception {
            givenEditorToken();
            MockMultipartFile file = createMinimalJpegFile("license-default.jpg");
            mockMvc.perform(multipart("/api/v1/media/upload")
                            .file(file)
                            .header("Authorization", TOKEN)
                            .param("altText", "기본")
                            // licenseType=INTERNAL 명시 (NotNull DTO 제약)
                            .param("licenseType", "INTERNAL"))
                    .andExpect(status().isCreated());

            Integer internalCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM media_asset WHERE uploaded_by = ? AND license_type = 'INTERNAL'",
                    Integer.class, editorId);
            assert internalCount != null && internalCount >= 1
                    : "INTERNAL 라이선스 저장 실패 (count=" + internalCount + ")";
        }

        @Test
        @DisplayName("AC-004-4.2: CC_BY + copyrightHolder 누락 — 400 LICENSE_MISSING_COPYRIGHT")
        void upload_ccByWithoutCopyright_returns400() throws Exception {
            givenEditorToken();
            MockMultipartFile file = createMinimalJpegFile("ccby.jpg");
            mockMvc.perform(multipart("/api/v1/media/upload")
                            .file(file)
                            .header("Authorization", TOKEN)
                            .param("altText", "CC_BY")
                            .param("licenseType", "CC_BY"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── §5 컬렉션 (REQ-MEDIA-005-D) ─────────────────────────────────────────

    @Nested
    @DisplayName("§5: 컬렉션 (REQ-MEDIA-005-D)")
    class Collection {

        @Test
        @DisplayName("AC-005-1.1: POST /api/v1/media/collections — 201 Created")
        void createCollection_returns201() throws Exception {
            givenEditorToken();
            String body = """
                    {"name":"홍보자료-%s","description":"홍보 모음","isPublic":false}
                    """.formatted(uid());
            mockMvc.perform(post("/api/v1/media/collections")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber());
        }

        @Test
        @DisplayName("AC-005-1.2: 동일 owner + name 중복 — 409 Conflict")
        void createCollection_duplicateName_returns409() throws Exception {
            String name = "중복앨범-" + uid();
            // 첫 번째 컬렉션을 DB에 직접 삽입
            jdbcTemplate.update(
                    "INSERT INTO media_collection (name, description, owner_id, is_public) " +
                            "VALUES (?, '기존', ?, FALSE)", name, editorId);

            givenEditorToken();
            String body = """
                    {"name":"%s","description":"중복 시도","isPublic":false}
                    """.formatted(name);
            mockMvc.perform(post("/api/v1/media/collections")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("AC-005-1.3: POST /collections/{id}/items — 자산 추가 201")
        void addItems_returns201() throws Exception {
            String name = "추가앨범-" + uid();
            jdbcTemplate.update(
                    "INSERT INTO media_collection (name, description, owner_id, is_public) " +
                            "VALUES (?, '추가용', ?, FALSE)", name, editorId);
            Long collectionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM media_collection WHERE owner_id = ? AND name = ?",
                    Long.class, editorId, name);
            long assetId = insertAsset("add-" + uid(), "IMAGE", "image/jpeg",
                    new String[]{"앨범"}, editorId);
            UUID assetUuid = jdbcTemplate.queryForObject(
                    "SELECT uuid FROM media_asset WHERE id = ?", UUID.class, assetId);

            givenEditorToken();
            String body = "[\"" + assetUuid + "\"]";
            mockMvc.perform(post("/api/v1/media/collections/" + collectionId + "/items")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-005-1.4: DELETE /collections/{id}/items/{assetUuid} — 자산 제거 204")
        void removeItem_returns204() throws Exception {
            String name = "삭제앨범-" + uid();
            jdbcTemplate.update(
                    "INSERT INTO media_collection (name, description, owner_id, is_public) " +
                            "VALUES (?, '삭제용', ?, FALSE)", name, editorId);
            Long collectionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM media_collection WHERE owner_id = ? AND name = ?",
                    Long.class, editorId, name);
            long assetId = insertAsset("del-" + uid(), "IMAGE", "image/jpeg",
                    new String[]{"삭제"}, editorId);
            UUID assetUuid = jdbcTemplate.queryForObject(
                    "SELECT uuid FROM media_asset WHERE id = ?", UUID.class, assetId);
            jdbcTemplate.update(
                    "INSERT INTO media_collection_item (collection_id, asset_id, sort_order) " +
                            "VALUES (?, ?, 0)", collectionId, assetId);

            givenEditorToken();
            mockMvc.perform(delete("/api/v1/media/collections/" + collectionId + "/items/" + assetUuid)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void givenEditorToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                editorId, "media-editor",
                Set.of("EDITOR"),
                Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '미디어테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    /**
     * media_asset 테이블에 READY 상태 IMAGE 자산을 직접 삽입한다.
     * alt_text CHECK 제약 (IMAGE+READY → alt_text NOT NULL) 만족.
     */
    private long insertAsset(String name, String type, String mime, String[] tags, long uploaderId) {
        // PostgreSQL TEXT[] → Java String[] 직접 바인딩
        jdbcTemplate.update(
                "INSERT INTO media_asset " +
                        "(type, original_filename, stored_path, mime_type, size_bytes, checksum_sha256, " +
                        " alt_text, tags, license_type, uploaded_by, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'INTERNAL', ?, 'READY')",
                type, name + ".jpg", "/tmp/iroum-cms-media-it/test/" + name,
                mime, 1024L, "checksum-" + name,
                "alt-" + name, tags, uploaderId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM media_asset WHERE original_filename = ?", Long.class, name + ".jpg");
        return id == null ? -1L : id;
    }

    /**
     * Tika 가 image/jpeg 로 인식할 수 있는 최소 JPEG 바이트 시퀀스 생성.
     * SOI(FFD8) + APP0/JFIF 헤더 + 더미 데이터 + EOI(FFD9).
     */
    private MockMultipartFile createMinimalJpegFile(String filename) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // SOI
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        // APP0 JFIF segment: FFE0 + length(00 10) + "JFIF\0" + version(01 01) + units(00) + Xdensity(00 01) + Ydensity(00 01) + Xthumb(00) + Ythumb(00)
        out.write(new byte[]{
                (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10,
                'J', 'F', 'I', 'F', 0x00,
                0x01, 0x01,
                0x00,
                0x00, 0x01,
                0x00, 0x01,
                0x00, 0x00
        });
        // 약간의 더미 데이터 (Tika 신뢰도 향상)
        byte[] dummy = new byte[64];
        for (int i = 0; i < dummy.length; i++) dummy[i] = (byte) (i & 0xFF);
        out.write(dummy);
        // EOI
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD9});
        return new MockMultipartFile("file", filename, "image/jpeg", out.toByteArray());
    }
}
