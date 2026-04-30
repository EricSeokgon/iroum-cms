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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BannerService RED→GREEN 테스트.
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

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-009-D-1: alt_text + 기간 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("alt_text 비어있으면 BannerAltTextMissingException 발생 (KWCAG 1.1.1)")
    void shouldRejectBannerWithEmptyAltText() {
        // Arrange
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        BannerRequest request = stubRequest(from, until, ""); // 빈 alt_text

        // Act & Assert
        assertThatThrownBy(() -> bannerService.registerBanner(request))
                .isInstanceOf(BannerAltTextMissingException.class);
    }

    @Test
    @DisplayName("display_from >= display_until 이면 BannerPeriodInvalidException 발생")
    void shouldRegisterBannerWithPeriodAndAltValidation() {
        // Arrange — from > until (역전)
        Instant from = Instant.now().plusSeconds(3600);
        Instant until = Instant.now();
        BannerRequest request = stubRequest(from, until, "유효한 대체텍스트");

        // Act & Assert
        assertThatThrownBy(() -> bannerService.registerBanner(request))
                .isInstanceOf(BannerPeriodInvalidException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-009-D-2: 그룹 + 시간 윈도우 필터
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bannerGroupCode + 시간 윈도우 필터로 ACTIVE 배너만 반환")
    void shouldGetActiveBannersByGroupAndTimeWindow() {
        // Arrange
        Banner banner = stubBanner(1L, 0);
        when(bannerMapper.findActiveByGroupAndTimeWindow(any(), any())).thenReturn(List.of(banner));

        // Act
        List<BannerResponse> result = bannerService.getActiveBannersByGroup("HOME_HERO");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).bannerGroupCode()).isEqualTo("HOME_HERO");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-009-D-2: sort_order ASC 정렬
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 배너는 sortOrder ASC 로 정렬되어 반환")
    void shouldOrderBannersBySortOrderAsc() {
        // Arrange — DB 쿼리에서 ASC로 이미 정렬되어 반환된다고 가정
        Banner b1 = stubBanner(1L, 1);
        Banner b2 = stubBanner(2L, 5);
        when(bannerMapper.findActiveByGroupAndTimeWindow(any(), any())).thenReturn(List.of(b1, b2));

        // Act
        List<BannerResponse> result = bannerService.getActiveBannersByGroup("HOME_HERO");

        // Assert
        assertThat(result.get(0).sortOrder()).isLessThanOrEqualTo(result.get(1).sortOrder());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-009-D-3: 클릭 카운트 원자적 증가
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("클릭 이벤트 발생 시 incrementClickCount 호출 (원자적 UPDATE)")
    void shouldIncrementClickCountAtomically() {
        // Arrange
        Banner banner = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(banner));
        when(bannerMapper.incrementClickCount(1L)).thenReturn(1);

        // Act
        bannerService.recordClick(1L);

        // Assert — incrementClickCount 호출 확인
        verify(bannerMapper).incrementClickCount(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-009-D-3: audit_log 기록
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("클릭 이벤트 발생 시 auditLogService.record 호출")
    void shouldLogClickToAuditTable() {
        // Arrange
        Banner banner = stubBanner(1L, 0);
        when(bannerMapper.findById(1L)).thenReturn(Optional.of(banner));
        when(bannerMapper.incrementClickCount(1L)).thenReturn(1);

        // Act
        bannerService.recordClick(1L);

        // Assert — audit 기록 확인
        verify(auditLogService).record(any(AuditLogService.AuditLogRecord.class));
    }
}
