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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PopupService 테스트.
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

    // ──────────────────────────────────────────────
    // registerPopup
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("show_from >= show_until 이면 PopupPeriodInvalidException 발생")
    void shouldRejectPopupWithInvalidPeriod() {
        Instant from = Instant.now().plusSeconds(3600);
        Instant until = Instant.now();
        PopupRequest request = stubRequest(from, until, "ALL", null);

        assertThatThrownBy(() -> popupService.registerPopup(request))
                .isInstanceOf(PopupPeriodInvalidException.class);
        verify(popupMapper, never()).insert(any());
    }

    @Test
    @DisplayName("targetType=ROLE 이고 targetRoleCodes 비어있으면 PopupTargetMissingException 발생")
    void shouldRejectPopupWithEmptyTargetRoleCodesWhenTargetTypeRole() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ROLE", List.of());

        assertThatThrownBy(() -> popupService.registerPopup(request))
                .isInstanceOf(PopupTargetMissingException.class);
    }

    @Test
    @DisplayName("targetType=ROLE 이고 targetRoleCodes null이면 PopupTargetMissingException 발생")
    void shouldRejectPopupWithNullTargetRoleCodesWhenRole() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ROLE", null);

        assertThatThrownBy(() -> popupService.registerPopup(request))
                .isInstanceOf(PopupTargetMissingException.class);
    }

    @Test
    @DisplayName("targetType=ROLE 이고 targetRoleCodes 정상이면 INSERT 수행")
    void shouldRegisterPopupWithRoleAndCodes() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ROLE", List.of("ADMIN"));

        doAnswer(inv -> {
            Popup p = inv.getArgument(0);
            p.setId(1L);
            return null;
        }).when(popupMapper).insert(any(Popup.class));

        PopupResponse result = popupService.registerPopup(request);

        verify(popupMapper).insert(any());
        assertThat(result.targetType()).isEqualTo("ROLE");
        assertThat(result.targetRoleCodes()).contains("ADMIN");
    }

    @Test
    @DisplayName("contentHtml에 script 태그 포함 시 Jsoup sanitize로 제거되어 저장")
    void shouldSanitizeContentHtmlOnRegister() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ALL", null);

        doAnswer(inv -> {
            Popup popup = inv.getArgument(0);
            popup.setId(1L);
            return null;
        }).when(popupMapper).insert(any(Popup.class));

        PopupResponse response = popupService.registerPopup(request);

        assertThat(response.contentHtml()).doesNotContain("<script>");
        assertThat(response.contentHtml()).doesNotContain("alert");
    }

    @Test
    @DisplayName("registerPopup — position null이면 CENTER 기본값")
    void registerPopup_nullPosition_defaultsCenter() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = new PopupRequest(
                1L, "T", "<p>c</p>", null, null, null, null, null,
                from, until, null, null, null, null);

        doAnswer(inv -> {
            Popup p = inv.getArgument(0);
            p.setId(1L);
            return null;
        }).when(popupMapper).insert(any(Popup.class));

        popupService.registerPopup(request);

        ArgumentCaptor<Popup> captor = ArgumentCaptor.forClass(Popup.class);
        verify(popupMapper).insert(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo("CENTER");
        assertThat(captor.getValue().getWidth()).isEqualTo(400);
        assertThat(captor.getValue().getHeight()).isEqualTo(300);
        assertThat(captor.getValue().isShowTodayClose()).isTrue();
        assertThat(captor.getValue().getDisplayPriority()).isZero();
        assertThat(captor.getValue().getTargetType()).isEqualTo("ALL");
    }

    @Test
    @DisplayName("registerPopup — showTodayClose=true 시 cookieKey 자동 생성")
    void registerPopup_setsCookieKeyWhenShowTodayClose() {
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest request = stubRequest(from, until, "ALL", null);

        doAnswer(inv -> {
            Popup p = inv.getArgument(0);
            p.setId(123L);
            return null;
        }).when(popupMapper).insert(any(Popup.class));

        PopupResponse response = popupService.registerPopup(request);

        assertThat(response.id()).isEqualTo(123L);
        assertThat(response.showTodayClose()).isTrue();
    }

    // ──────────────────────────────────────────────
    // updatePopup
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("updatePopup — 존재하지 않으면 IllegalArgumentException")
    void updatePopup_throws_when_not_found() {
        when(popupMapper.findById(99L)).thenReturn(Optional.empty());
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest req = stubRequest(from, until, "ALL", null);

        assertThatThrownBy(() -> popupService.updatePopup(99L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updatePopup — 기간 역전 시 PopupPeriodInvalidException")
    void updatePopup_invalidPeriod_throws() {
        Popup existing = stubPopup(1L, "ALL", 0, Instant.now(), Instant.now().plusSeconds(60));
        when(popupMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now().plusSeconds(3600);
        Instant until = Instant.now();
        PopupRequest req = stubRequest(from, until, "ALL", null);

        assertThatThrownBy(() -> popupService.updatePopup(1L, req))
                .isInstanceOf(PopupPeriodInvalidException.class);
    }

    @Test
    @DisplayName("updatePopup — targetType=ROLE인데 codes 없으면 PopupTargetMissingException")
    void updatePopup_roleWithoutCodes_throws() {
        Popup existing = stubPopup(1L, "ALL", 0, Instant.now(), Instant.now().plusSeconds(60));
        when(popupMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest req = stubRequest(from, until, "ROLE", List.of());

        assertThatThrownBy(() -> popupService.updatePopup(1L, req))
                .isInstanceOf(PopupTargetMissingException.class);
        verify(popupMapper, never()).update(any());
    }

    @Test
    @DisplayName("updatePopup — 정상 흐름에서 update 호출 + 응답 반환")
    void updatePopup_happyPath() {
        Popup existing = stubPopup(1L, "ALL", 0, Instant.now(), Instant.now().plusSeconds(60));
        when(popupMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest req = new PopupRequest(
                1L, "수정된 제목", "<p>안전한 내용</p>",
                "TOP_RIGHT", 0, 0, 500, 350,
                from, until, false, 5, "MEMBER", List.of());

        PopupResponse result = popupService.updatePopup(1L, req);

        verify(popupMapper).update(any(Popup.class));
        assertThat(result.title()).isEqualTo("수정된 제목");
        assertThat(result.position()).isEqualTo("TOP_RIGHT");
        assertThat(result.targetType()).isEqualTo("MEMBER");
        assertThat(result.displayPriority()).isEqualTo(5);
    }

    @Test
    @DisplayName("updatePopup — targetType null 시 기존 targetType 유지")
    void updatePopup_nullTargetType_keepsExisting() {
        Popup existing = stubPopup(1L, "MEMBER", 0, Instant.now(), Instant.now().plusSeconds(60));
        when(popupMapper.findById(1L)).thenReturn(Optional.of(existing));
        Instant from = Instant.now();
        Instant until = from.plusSeconds(3600);
        PopupRequest req = new PopupRequest(
                1L, "T", "<p>c</p>", null, null, null, null, null,
                from, until, null, null, null, null);

        PopupResponse result = popupService.updatePopup(1L, req);
        assertThat(result.targetType()).isEqualTo("MEMBER");
    }

    // ──────────────────────────────────────────────
    // deletePopup
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("deletePopup — 존재하지 않으면 IllegalArgumentException")
    void deletePopup_throws_when_not_found() {
        when(popupMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> popupService.deletePopup(99L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(popupMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("deletePopup — 정상 삭제 호출")
    void deletePopup_callsDelete() {
        Popup existing = stubPopup(1L, "ALL", 0, Instant.now(), Instant.now().plusSeconds(60));
        when(popupMapper.findById(1L)).thenReturn(Optional.of(existing));

        popupService.deletePopup(1L);

        verify(popupMapper).deleteById(1L);
    }

    // ──────────────────────────────────────────────
    // getPopupsBySite
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getPopupsBySite — siteId의 모든 팝업 반환")
    void getPopupsBySite_returnsAll() {
        Popup p1 = stubPopup(1L, "ALL", 0, Instant.now(), Instant.now().plusSeconds(60));
        Popup p2 = stubPopup(2L, "ALL", 0, Instant.now(), Instant.now().plusSeconds(60));
        when(popupMapper.findBySiteId(1L)).thenReturn(List.of(p1, p2));

        List<PopupResponse> result = popupService.getPopupsBySite(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getPopupsBySite — 빈 결과")
    void getPopupsBySite_empty() {
        when(popupMapper.findBySiteId(99L)).thenReturn(List.of());

        List<PopupResponse> result = popupService.getPopupsBySite(99L);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // getActivePopups
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("status=ACTIVE AND show_from <= now <= show_until 조건 팝업만 반환")
    void shouldGetActivePopupsByTimeWindow() {
        Instant now = Instant.now();
        Popup active = stubPopup(1L, "ALL", 0, now.minusSeconds(60), now.plusSeconds(3600));
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(active));

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("활성 팝업은 displayPriority DESC 로 정렬되어 반환")
    void shouldOrderPopupsByDisplayPriorityDesc() {
        Instant now = Instant.now();
        Popup low = stubPopup(1L, "ALL", 1, now.minusSeconds(60), now.plusSeconds(3600));
        Popup high = stubPopup(2L, "ALL", 10, now.minusSeconds(60), now.plusSeconds(3600));
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(low, high));

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result.get(0).displayPriority()).isGreaterThan(result.get(1).displayPriority());
    }

    @Test
    @DisplayName("활성 팝업이 5개 초과이면 상위 5개만 반환 (X-Popup-Limit:5)")
    void shouldLimitActivePopupsTo5() {
        Instant now = Instant.now();
        List<Popup> many = List.of(
                stubPopup(1L, "ALL", 10, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(2L, "ALL", 9, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(3L, "ALL", 8, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(4L, "ALL", 7, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(5L, "ALL", 6, now.minusSeconds(60), now.plusSeconds(3600)),
                stubPopup(6L, "ALL", 5, now.minusSeconds(60), now.plusSeconds(3600))
        );
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(many);

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("show_today_close=true 이면 응답에 cookieKey 포함")
    void shouldExposeCookieKeyForTodayClose() {
        Instant now = Instant.now();
        Popup popup = stubPopup(1L, "ALL", 0, now.minusSeconds(60), now.plusSeconds(3600));
        popup.setShowTodayClose(true);
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(popup));

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result.get(0).cookieKey()).isNotNull();
        assertThat(result.get(0).showTodayClose()).isTrue();
    }

    @Test
    @DisplayName("show_today_close=false 이면 cookieKey null")
    void shouldOmitCookieKeyWhenTodayCloseFalse() {
        Instant now = Instant.now();
        Popup popup = stubPopup(1L, "ALL", 0, now.minusSeconds(60), now.plusSeconds(3600));
        popup.setShowTodayClose(false);
        popup.setCookieKey(null);
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(popup));

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result.get(0).cookieKey()).isNull();
        assertThat(result.get(0).showTodayClose()).isFalse();
    }

    @Test
    @DisplayName("targetType ALL/MEMBER/ROLE 팝업이 모두 반환되고 targetType 필드 포함")
    void shouldFilterPopupsByTargetType() {
        Instant now = Instant.now();
        Popup all = stubPopup(1L, "ALL", 3, now.minusSeconds(60), now.plusSeconds(3600));
        Popup member = stubPopup(2L, "MEMBER", 2, now.minusSeconds(60), now.plusSeconds(3600));
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of(all, member));

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result).extracting(PopupActiveResponse::targetType)
                .containsExactlyInAnyOrder("ALL", "MEMBER");
    }

    @Test
    @DisplayName("getActivePopups — 빈 결과")
    void getActivePopups_empty() {
        when(popupMapper.findActiveByTimeWindow(any(), any())).thenReturn(List.of());

        List<PopupActiveResponse> result = popupService.getActivePopups(1L);

        assertThat(result).isEmpty();
    }
}
