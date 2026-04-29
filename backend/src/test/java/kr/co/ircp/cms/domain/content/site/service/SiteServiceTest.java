package kr.co.ircp.cms.domain.content.site.service;

import kr.co.ircp.cms.domain.content.site.dto.SiteResponse;
import kr.co.ircp.cms.domain.content.site.dto.SiteUpdateRequest;
import kr.co.ircp.cms.domain.content.site.entity.Site;
import kr.co.ircp.cms.domain.content.site.exception.SiteMultiDisabledException;
import kr.co.ircp.cms.domain.content.site.mapper.SiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * SiteService RED 단계 테스트.
 * REQ-CONTENT-003-D: 사이트 마스터 관리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SiteService RED 테스트 (REQ-CONTENT-003-D)")
class SiteServiceTest {

    @Mock
    private SiteMapper siteMapper;

    private SiteService siteService;

    @BeforeEach
    void setUp() {
        siteService = new SiteServiceImpl(siteMapper);
    }

    private Site stubSite() {
        return Site.builder()
                .id(1L)
                .code("MAIN")
                .name("이로움 CMS")
                .domain("cms.ircp.co.kr")
                .defaultLanguage("ko")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-003-D-1: 도메인 기반 사이트 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("도메인으로 현재 사이트 조회 — 매칭 성공")
    void shouldReturnCurrentSiteByDomain() {
        // Arrange
        Site site = stubSite();
        when(siteMapper.findByDomain("cms.ircp.co.kr")).thenReturn(Optional.of(site));

        // Act
        SiteResponse response = siteService.getCurrentSite("cms.ircp.co.kr");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("MAIN");
        assertThat(response.domain()).isEqualTo("cms.ircp.co.kr");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-003-D-2: 도메인 불일치 시 기본 사이트 폴백
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("도메인 불일치 시 기본(MAIN) 사이트로 폴백")
    void shouldFallbackToDefaultSiteWhenDomainMismatch() {
        // Arrange
        Site defaultSite = stubSite();
        when(siteMapper.findByDomain("unknown.example.com")).thenReturn(Optional.empty());
        when(siteMapper.findByCode("MAIN")).thenReturn(Optional.of(defaultSite));

        // Act
        SiteResponse response = siteService.getCurrentSite("unknown.example.com");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("MAIN");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-003-D-3: 멀티사이트 비활성화 가드
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("멀티사이트 비활성화 상태에서 createSite 호출 시 SiteMultiDisabledException 발생")
    void shouldRejectMultiSiteCreationWhenDisabled() {
        // Arrange
        SiteUpdateRequest request = new SiteUpdateRequest("신규 사이트", "new.example.com", "ko", null);

        // Act & Assert
        assertThatThrownBy(() -> siteService.createSite(request))
                .isInstanceOf(SiteMultiDisabledException.class);
    }
}
