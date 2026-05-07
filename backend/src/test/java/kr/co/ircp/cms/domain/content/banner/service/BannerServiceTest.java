package kr.co.ircp.cms.domain.content.banner.service;

import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.content.banner.dto.BannerRequest;
import kr.co.ircp.cms.domain.content.banner.dto.BannerResponse;
import kr.co.ircp.cms.domain.content.banner.entity.Banner;
import kr.co.ircp.cms.domain.content.banner.exception.BannerAltTextMissingException;
import kr.co.ircp.cms.domain.content.banner.exception.BannerPeriodInvalidException;
import kr.co.ircp.cms.domain.content.banner.mapper.BannerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BannerService 단위 테스트.
 * REQ-CONTENT-009-D: 배너 CRUD + 클릭 카운트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BannerService 테스트 (REQ-CONTENT-009-D)")
class BannerServiceTest {

    @Mock private BannerMapper bannerMapper;
    @Mock private AuditLogService auditLogService;

    private BannerService bannerService;

    @BeforeEach
    void setUp() {
        bannerService = new BannerServiceImpl(bannerMapper, auditLogService);
    }

    private BannerRequest stubRequest(Instant from, Instant until, String altText) {
        return new BannerRequest(
                1L, "HOME_HERO", "배너 제목",
                "https://example.com/img.jpg",
                "https://example.com",
                "_blank",
                altText,
                from, until, 0
        );
    }

    private Banner stubBanner(long id, int sortOrder) {
        Instant now = Instant.now();
        return Banner.builder()
                .id(id)
                .siteId(1L)
                .bannerGroupCode("HOME_HERO")
                .title("배너 " + id)
                .imageUrl("https://example.com/img.jpg")
                .altText("배너 대체텍스트")
                .displayFrom(now.minusSeconds(60))
                .displayUntil(now.plusSeconds(3600))
                .sortOrder(sortOrder)
                .clickCount(0L)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ──────────────────────────────────────────────
    // registerBanner — 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("alt_text 비어있으면 BannerAltTextMissingException 발생 (KWCAG 1.1.1)")
    void shouldRejectBannerWithEmptyAltText() {
        // Arrange
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest request = stubRequest(from, until, "");

        // Act & Assert
        assertThatThrownBy(() -> bannerService.registerBanner(request))
                .isInstanceOf(BannerAltTextMissingException.class);
        verify(bannerMapper, never()).insert(any());
    }

    @Test
    @DisplayName("alt_text null이면 BannerAltTextMissingException 발생")
    void shouldRejectBannerWithNullAltText() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest request = stubRequest(from, until, null);

        assertThatThrownBy(() -> bannerService.registerBanner(request))
                .isInstanceOf(BannerAltTextMissingException.class);
    }

    @Test
    @DisplayName("display_from >= display_until 이면 BannerPeriodInvalidException 발생")
    void shouldRegisterBannerWithPeriodAndAltValidation() {
        Instant from = Instant.now().plusSeconds(3600);
        Instant until = Instant.now();
        BannerRequest request = stubRequest(from, until, "유효한 대체텍스트");

        assertThatThrownBy(() -> bannerService.registerBanner(request))
                .isInstanceOf(BannerPeriodInvalidException.class);
    }

    @Test
    @DisplayName("display_from == display_until 이면 BannerPeriodInvalidException 발생")
    void shouldRejectEqualPeriod() {
        Instant t = Instant.now();
        BannerRequest request = stubRequest(t, t, "유효한 대체텍스트");

        assertThatThrownBy(() -> bannerService.registerBanner(request))
                .isInstanceOf(BannerPeriodInvalidException.class);
    }

    @Test
    @DisplayName("registerBanner — 정상 흐름에서 INSERT 후 응답 반환")
    void registerBanner_happyPath() {
        // given
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest request = stubRequest(from, until, "배너 대체텍스트");

        // when
        BannerResponse result = bannerService.registerBanner(request);

        // then
        verify(bannerMapper).insert(any(Banner.class));
        assertThat(result.bannerGroupCode()).isEqualTo("HOME_HERO");
        assertThat(result.altText()).isEqualTo("배너 대체텍스트");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("registerBanner — linkTarget null이면 _self 기본값")
    void registerBanner_nullLinkTarget_defaultsSelf() {
        // given — linkTarget null로 request 생성
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest request = new BannerRequest(
                1L, "HOME_HERO", "제목", "https://x", "https://y",
                null, "alt", from, until, null);

        // when
        bannerService.registerBanner(request);

        // then
        ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
        verify(bannerMapper).insert(captor.capture());
        assertThat(captor.getValue().getLinkTarget()).isEqualTo("_self");
        assertThat(captor.getValue().getSortOrder()).isZero();
    }

    @Test
    @DisplayName("registerBanner — sortOrder null이면 0 기본값")
    void registerBanner_nullSortOrder_defaultsZero() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest request = new BannerRequest(
                1L, "G", "제목", "img", "link", "_blank", "alt", from, until, null);

        bannerService.registerBanner(request);

        ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
        verify(bannerMapper).insert(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isZero();
    }

    // ──────────────────────────────────────────────
    // updateBanner
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("updateBanner — 존재하지 않으면 IllegalArgumentException")
    void updateBanner_throws_when_not_found() {
        when(bannerMapper.findById(99L)).thenReturn(Optional.empty());
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest req = stubRequest(from, until, "alt");

        assertThatThrownBy(() -> bannerService.updateBanner(99L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateBanner — alt_text 비어있으면 BannerAltTextMissingException")
    void updateBanner_emptyAltText_throws() {
        Banner existing = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest req = stubRequest(from, until, "");

        assertThatThrownBy(() -> bannerService.updateBanner(1L, req))
                .isInstanceOf(BannerAltTextMissingException.class);
        verify(bannerMapper, never()).update(any());
    }

    @Test
    @DisplayName("updateBanner — 기간 역전이면 BannerPeriodInvalidException")
    void updateBanner_invalidPeriod_throws() {
        Banner existing = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now().plusSeconds(3600);
        Instant until = Instant.now();
        BannerRequest req = stubRequest(from, until, "alt");

        assertThatThrownBy(() -> bannerService.updateBanner(1L, req))
                .isInstanceOf(BannerPeriodInvalidException.class);
    }

    @Test
    @DisplayName("updateBanner — 정상 흐름에서 update 호출 + 응답 반환")
    void updateBanner_happyPath() {
        Banner existing = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest req = new BannerRequest(
                1L, "HOME_HERO", "수정된 제목", "img2", "link2", "_blank",
                "수정된 alt", from, until, 7);

        BannerResponse result = bannerService.updateBanner(1L, req);

        verify(bannerMapper).update(any(Banner.class));
        assertThat(result.title()).isEqualTo("수정된 제목");
        assertThat(result.altText()).isEqualTo("수정된 alt");
        assertThat(result.sortOrder()).isEqualTo(7);
    }

    @Test
    @DisplayName("updateBanner — sortOrder null 시 기존 값 유지")
    void updateBanner_nullSortOrder_keepsExisting() {
        Banner existing = stubBanner(1L, 5);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest req = new BannerRequest(
                1L, "HOME_HERO", "제목", "img", "link", "_blank",
                "alt", from, until, null);

        bannerService.updateBanner(1L, req);

        ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
        verify(bannerMapper).update(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
    }

    // ──────────────────────────────────────────────
    // deleteBanner
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("deleteBanner — 존재하지 않으면 IllegalArgumentException")
    void deleteBanner_throws_when_not_found() {
        when(bannerMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.deleteBanner(99L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bannerMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteBanner — 정상 삭제 호출")
    void deleteBanner_callsDelete() {
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(stubBanner(1L, 0)));

        bannerService.deleteBanner(1L);

        verify(bannerMapper).deleteById(1L);
    }

    // ──────────────────────────────────────────────
    // getActiveBannersByGroup
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("bannerGroupCode + 시간 윈도우 필터로 ACTIVE 배너만 반환")
    void shouldGetActiveBannersByGroupAndTimeWindow() {
        Banner banner = stubBanner(1L, 0);
        when(bannerMapper.findActiveByGroupAndTimeWindow(any(), any())).thenReturn(List.of(banner));

        List<BannerResponse> result = bannerService.getActiveBannersByGroup("HOME_HERO");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bannerGroupCode()).isEqualTo("HOME_HERO");
    }

    @Test
    @DisplayName("활성 배너는 sortOrder ASC 로 정렬되어 반환")
    void shouldOrderBannersBySortOrderAsc() {
        Banner b1 = stubBanner(1L, 1);
        Banner b2 = stubBanner(2L, 5);
        when(bannerMapper.findActiveByGroupAndTimeWindow(any(), any())).thenReturn(List.of(b1, b2));

        List<BannerResponse> result = bannerService.getActiveBannersByGroup("HOME_HERO");

        assertThat(result.get(0).sortOrder()).isLessThanOrEqualTo(result.get(1).sortOrder());
    }

    @Test
    @DisplayName("getActiveBannersByGroup — 결과 없으면 빈 리스트")
    void getActiveBannersByGroup_empty() {
        when(bannerMapper.findActiveByGroupAndTimeWindow(any(), any())).thenReturn(List.of());

        List<BannerResponse> result = bannerService.getActiveBannersByGroup("UNKNOWN");

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // recordClick
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("recordClick — 존재하지 않으면 IllegalArgumentException + audit 호출 안 됨")
    void recordClick_throws_when_not_found() {
        when(bannerMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.recordClick(99L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bannerMapper, never()).incrementClickCount(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("클릭 이벤트 발생 시 incrementClickCount 호출 (원자적 UPDATE)")
    void shouldIncrementClickCountAtomically() {
        Banner banner = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(banner));
        when(bannerMapper.incrementClickCount(1L)).thenReturn(1);

        bannerService.recordClick(1L);

        verify(bannerMapper).incrementClickCount(1L);
    }

    @Test
    @DisplayName("클릭 이벤트 발생 시 auditLogService.record 호출")
    void shouldLogClickToAuditTable() {
        Banner banner = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(banner));
        when(bannerMapper.incrementClickCount(1L)).thenReturn(1);

        bannerService.recordClick(1L);

        verify(auditLogService).record(any(AuditLogService.AuditLogRecord.class));
    }
}
