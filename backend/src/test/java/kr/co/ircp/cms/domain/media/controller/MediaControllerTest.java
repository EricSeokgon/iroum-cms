package kr.co.ircp.cms.domain.media.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.media.dto.*;
import kr.co.ircp.cms.domain.media.entity.LicenseType;
import kr.co.ircp.cms.domain.media.entity.JobStatus;
import kr.co.ircp.cms.domain.media.entity.MediaAsset;
import kr.co.ircp.cms.domain.media.entity.MediaAssetUsage;
import kr.co.ircp.cms.domain.media.entity.MediaCollection;
import kr.co.ircp.cms.domain.media.entity.MediaStatus;
import kr.co.ircp.cms.domain.media.entity.MediaType;
import kr.co.ircp.cms.domain.media.exception.MediaAssetInUseException;
import kr.co.ircp.cms.domain.media.exception.MediaNotFoundException;
import kr.co.ircp.cms.domain.media.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import kr.co.ircp.cms.support.WebMvcTestInfraConfig;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MediaController 슬라이스 테스트.
 * REQ-MEDIA-001, REQ-MEDIA-003, REQ-MEDIA-004, REQ-MEDIA-005
 */
@WebMvcTest(MediaController.class)
@Import(WebMvcTestInfraConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class MediaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean MediaService mediaService;

    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new JwtPrincipal(1L, "testuser", Set.of("EDITOR"), Set.of());
    }

    @Test
    @DisplayName("GET /api/v1/media/{uuid} — 존재하는 자산 조회 성공")
    void detail_found_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        MediaAssetDetail detail = buildDetail(uuid);
        given(mediaService.findByUuid(uuid)).willReturn(detail);

        mockMvc.perform(get("/api/v1/media/{uuid}", uuid)
                .with(SecurityMockMvcRequestPostProcessors.user(principal.username()).roles("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(uuid.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/media/{uuid} — 존재하지 않는 자산 404 반환")
    void detail_notFound_returns404() throws Exception {
        UUID uuid = UUID.randomUUID();
        given(mediaService.findByUuid(uuid)).willThrow(new MediaNotFoundException(uuid));

        mockMvc.perform(get("/api/v1/media/{uuid}", uuid)
                .with(SecurityMockMvcRequestPostProcessors.user(principal.username()).roles("EDITOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/media/{uuid} — 사용 중인 자산 삭제 시 409 반환")
    void delete_assetInUse_returns409() throws Exception {
        UUID uuid = UUID.randomUUID();
        List<MediaAssetUsage> usages = List.of(
                MediaAssetUsage.builder().assetId(1L).usedIn("POST").referenceId(1L).referenceTable("bbs_post").build()
        );
        willThrow(new MediaAssetInUseException(usages)).given(mediaService).delete(uuid);

        mockMvc.perform(delete("/api/v1/media/{uuid}", uuid)
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.user(principal.username()).roles("EDITOR")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/v1/media/{uuid} — 삭제 성공 시 204 반환")
    void delete_success_returns204() throws Exception {
        UUID uuid = UUID.randomUUID();
        willDoNothing().given(mediaService).delete(uuid);

        mockMvc.perform(delete("/api/v1/media/{uuid}", uuid)
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.user(principal.username()).roles("EDITOR")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/media/{uuid}/url — 서명 URL 발급 성공")
    void signedUrl_success_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        MediaSignedUrl signedUrl = new MediaSignedUrl("/api/v1/media/" + uuid + "/download?sig=abc", Instant.now().plusSeconds(900));
        given(mediaService.generateSignedUrl(uuid, "original")).willReturn(signedUrl);

        mockMvc.perform(get("/api/v1/media/{uuid}/url", uuid)
                .param("variant", "original")
                .with(SecurityMockMvcRequestPostProcessors.user(principal.username()).roles("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedUrl").exists());
    }

    @Test
    @DisplayName("POST /api/v1/media/collections — 컬렉션 생성 성공")
    void createCollection_success_returns201() throws Exception {
        MediaCollectionCreateRequest req = new MediaCollectionCreateRequest("테스트 앨범", "설명", false);
        MediaCollectionSummary summary = new MediaCollectionSummary(1L, "테스트 앨범", "설명", 1L, false, 0, Instant.now());
        given(mediaService.createCollection(any(), eq(1L))).willReturn(summary);

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_EDITOR")));
        mockMvc.perform(post("/api/v1/media/collections")
                .with(csrf())
                .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("테스트 앨범"));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private MediaAssetDetail buildDetail(UUID uuid) {
        return new MediaAssetDetail(
                1L, uuid, MediaType.IMAGE, "test.jpg", null,
                "image/jpeg", 1024L, "abc123", 100, 100, null,
                false, null, "{}", "테스트", null, List.of(),
                null, "INTERNAL", null, 1L, MediaStatus.READY,
                Instant.now(), Instant.now()
        );
    }
}
