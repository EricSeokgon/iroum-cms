package kr.co.ircp.cms.domain.content.popup.service;

import kr.co.ircp.cms.domain.content.popup.dto.PopupActiveResponse;
import kr.co.ircp.cms.domain.content.popup.dto.PopupRequest;
import kr.co.ircp.cms.domain.content.popup.dto.PopupResponse;
import kr.co.ircp.cms.domain.content.popup.entity.Popup;
import kr.co.ircp.cms.domain.content.popup.exception.PopupPeriodInvalidException;
import kr.co.ircp.cms.domain.content.popup.exception.PopupTargetMissingException;
import kr.co.ircp.cms.domain.content.popup.mapper.PopupMapper;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * PopupService RED→GREEN 테스트.
 * REQ-CONTENT-008-D: 팝업 CRUD + 활성 팝업 조회
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PopupService 테스트 (REQ-CONTENT-008-D)")
class PopupServiceTest {

    @Mock private PopupMapper popupMapper;

    private PopupService popupService;

    @BeforeEach
    void setUp() {
        popupService = new PopupServiceImpl(popupMapper);
    }

    private PopupRequest stubRequest(Instant showFrom, Instant showUntil, String targetType, List<String> roleCodes) {
        return new PopupRequest(
                1L,
                "팝업 제목",
                "<p>팝업 <script>alert('xss')</script>내용</p>",
                "CENTER", null, null, 400, 300,
                showFrom, showUntil, true, 0,
                targetType, roleCodes
        );
    }

    private Popup stubPopup(long id, String targetType, int priority, Instant showFrom, Instant showUntil) {
        return Popup.builder()
                .id(id)
                .siteId(1L)
                .title("팝업 " + id)
                .contentHtml("<p>내용</p>")
                .position("CENTER")
                .width(400)
                .height(300)
                .showFrom(showFrom)
                .showUntil(showUntil)
                .showTodayClose(true)
                .displayPriority(priority)
                .targetType(targetType)
                .targetRoleCodes(List.of())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-1: show_from < show_until 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("show_from >= show_until 이면 PopupPeriodInvalidException 발생")
    void shouldRejectPopupWithInvalidPeriod() {
        // Arrange
        Instant from = Instant.now().plusSeconds(3600);
        Instant until = Instant.now(); // until < from
        PopupRequest request = stubRequest(from, until, "ALL", null);

        // Act & Assert
        assertThatThrownBy(() -> popupService.registerPopup(request))
                .isInstanceOf(PopupPeriodInvalidException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-1: ROLE 타겟 시 역할 코드 필수
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("targetType=ROLE 이고 targetRoleCodes 비어있으면 PopupTargetMissingException 발생")
    void shouldRejectPopupWithEmptyTargetRoleCodesWhenTargetTypeRole() {
        // Arrange
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ROLE", List.of()); // 빈 리스트

        // Act & Assert
        assertThatThrownBy(() -> popupService.registerPopup(request))
                .isInstanceOf(PopupTargetMissingException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-1: HTML sanitize (XSS 방어)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("contentHtml에 script 태그 포함 시 Jsoup sanitize로 제거되어 저장")
    void shouldSanitizeContentHtmlOnRegister() {
        // Arrange
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ALL", null);

        doAnswer(inv -> {
            Popup popup = inv.getArgument(0);
            popup.setId(1L);
            return null;
        }).when(popupMapper).insert(any(Popup.class));

        // Act
        PopupResponse response = popupService.registerPopup(request);

        // Assert — script 태그가 제거되어야 함
        assertThat(response.contentHtml()).doesNotContain("<script>");
        assertThat(response.contentHtml()).doesNotContain("alert");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-2: 활성 팝업 노출 시간 윈도우 필터
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("status=ACTIVE AND show_from <= now <= show_until 조건 팝업만 반환")
    void shouldGetActivePopupsByTimeWindow() {
        // Arrange
        Instant now = Instant.now();
        Popup active = stubPopup(1L, "ALL", 0, now.minusSeconds(60), now.plusSeconds(3600));
        // 실제 호출은 내부에서 now를 생성하므로 both args에 any() 사용
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(active));
        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-3: display_priority DESC 정렬
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 팝업은 displayPriority DESC 로 정렬되어 반환")
    void shouldOrderPopupsByDisplayPriorityDesc() {
        // Arrange
        Instant now = Instant.now();
        Popup low = stubPopup(1L, "ALL", 1, now.minusSeconds(60), now.plusSeconds(3600));
        Popup high = stubPopup(2L, "ALL", 10, now.minusSeconds(60), now.plusSeconds(3600));
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(low, high));

        // Act
        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        // Assert — priority DESC 정렬
        assertThat(result.get(0).displayPriority()).isGreaterThan(result.get(1).displayPriority());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-2: 활성 팝업 최대 5개 제한
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 팝업이 5개 초과이면 상위 5개만 반환 (X-Popup-Limit:5)")
    void shouldLimitActivePopupsTo5() {
        // Arrange
        Instant now = Instant.now();
        List<Popup> many = List.of(
                stubPopup(1L, "ALL", 10, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(2L, "ALL", 9, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(3L, "ALL", 8, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(4L, "ALL", 7, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(5L, "ALL", 6, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(6L, "ALL", 5, now.minusSeconds(60), now.plusSeconds(3600)) // 6번째
        );
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(many);

        // Act
        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        // Assert — 최대 5개
        assertThat(result).hasSize(5);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-2: show_today_close=true 시 cookie_key 응답 포함
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("show_today_close=true 이면 응답에 cookieKey 포함")
    void shouldExposeCookieKeyForTodayClose() {
        // Arrange
        Instant now = Instant.now();
        Popup popup = stubPopup(1L, "ALL", 0, now.minusSeconds(60), now.plusSeconds(3600));
        popup.setShowTodayClose(true);
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(popup));

        // Act
        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        // Assert — cookieKey 존재 (null이 아님)
        assertThat(result.get(0).cookieKey()).isNotNull();
        assertThat(result.get(0).showTodayClose()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-008-D-3: targetType 필터 (ALL/MEMBER/ROLE)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("targetType ALL/MEMBER/ROLE 팝업이 모두 반환되고 targetType 필드 포함")
    void shouldFilterPopupsByTargetType() {
        // Arrange
        Instant now = Instant.now();
        Popup all = stubPopup(1L, "ALL", 3, now.minusSeconds(60), now.plusSeconds(3600));
        Popup member = stubPopup(2L, "MEMBER", 2, now.minusSeconds(60), now.plusSeconds(3600));
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(all, member));

        // Act
        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        // Assert
        assertThat(result).extracting(PopupActiveResponse::targetType)
                .containsExactlyInAnyOrder("ALL", "MEMBER");
    }
}
