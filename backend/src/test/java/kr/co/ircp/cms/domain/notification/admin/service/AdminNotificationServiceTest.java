package kr.co.ircp.cms.domain.notification.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.admin.dto.AdminNotificationDto;
import kr.co.ircp.cms.domain.notification.admin.dto.MarkAllReadRequest;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;
import kr.co.ircp.cms.domain.notification.admin.exception.AdminNotificationNotFoundException;
import kr.co.ircp.cms.domain.notification.admin.repository.AdminNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AdminNotificationService 단위 테스트.
 *
 * <p>SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-001~005, 010 — 받은편지함 CRUD 및 권한 격리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNotificationService (REQ-NC-001~005, 010)")
class AdminNotificationServiceTest {

    @Mock
    private AdminNotificationMapper mapper;

    // SPEC-CMS-NOTIFICATION-WS-001 — insert() 가 이벤트를 발행하므로 publisher 주입(기존 테스트는 미사용).
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private AdminNotificationService service;

    private static final Long ADMIN_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new AdminNotificationService(mapper, eventPublisher);
    }

    private AdminNotification sample(Long id, String status) {
        return AdminNotification.builder()
                .id(id)
                .adminUserId(ADMIN_ID)
                .type("POST_APPROVAL_REQUEST")
                .severity("INFO")
                .title("승인 요청")
                .body("게시글 승인을 기다리는 항목이 있습니다.")
                .refType("POST")
                .refId(42L)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    // ─── REQ-NC-001 / AC-NC-001 ────────────────────────────────────────────

    @Test
    @DisplayName("getNotifications: 필터/페이지네이션 결과를 PageResponse 로 반환한다 (AC-NC-001-1)")
    void getNotifications_returnsPageResponse() {
        List<AdminNotification> rows = List.of(sample(1L, "UNREAD"), sample(2L, "UNREAD"));
        when(mapper.findFiltered(anyMap())).thenReturn(rows);
        when(mapper.countFiltered(anyMap())).thenReturn(50L);

        PageResponse<AdminNotificationDto> page = service.getNotifications(
                ADMIN_ID, List.of("UNREAD"), null, null, null, null, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(50L);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("getNotifications: 매퍼에 adminUserId / offset / size 가 정확히 전달된다 (REQ-NC-010)")
    void getNotifications_paramsPassedCorrectly() {
        when(mapper.findFiltered(anyMap())).thenReturn(List.of());
        when(mapper.countFiltered(anyMap())).thenReturn(0L);

        service.getNotifications(ADMIN_ID, List.of("UNREAD", "READ"),
                List.of("ERROR"), List.of("INTEGRATION_ERROR"), null, null, 2, 30);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findFiltered(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("adminUserId")).isEqualTo(ADMIN_ID);
        assertThat(p.get("offset")).isEqualTo(60);
        assertThat(p.get("size")).isEqualTo(30);
        assertThat(p.get("statusList")).asInstanceOf(list(String.class)).containsExactly("UNREAD", "READ");
        assertThat(p.get("severityList")).asInstanceOf(list(String.class)).containsExactly("ERROR");
        assertThat(p.get("typeList")).asInstanceOf(list(String.class)).containsExactly("INTEGRATION_ERROR");
    }

    @Test
    @DisplayName("getNotifications: size 가 100 을 초과해도 최대 100 으로 강제된다")
    void getNotifications_maxSizeIs100() {
        when(mapper.findFiltered(anyMap())).thenReturn(List.of());
        when(mapper.countFiltered(anyMap())).thenReturn(0L);

        service.getNotifications(ADMIN_ID, null, null, null, null, null, 0, 9999);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findFiltered(captor.capture());
        assertThat(captor.getValue().get("size")).isEqualTo(100);
    }

    // ─── REQ-NC-002 / AC-NC-002 ────────────────────────────────────────────

    @Test
    @DisplayName("markRead: 본인 소유 알림은 정상 처리된다 (AC-NC-002-1)")
    void markRead_success() {
        when(mapper.findByIdAndUser(eq(10L), eq(ADMIN_ID))).thenReturn(sample(10L, "UNREAD"));
        when(mapper.markRead(10L, ADMIN_ID)).thenReturn(1);

        service.markRead(10L, ADMIN_ID);

        verify(mapper).markRead(10L, ADMIN_ID);
    }

    @Test
    @DisplayName("markRead: 이미 READ 인 경우에도 멱등 성공한다 (AC-NC-002-2)")
    void markRead_idempotentOnAlreadyRead() {
        when(mapper.findByIdAndUser(eq(10L), eq(ADMIN_ID))).thenReturn(sample(10L, "READ"));

        // 예외 없이 통과해야 함 — UPDATE 가 0 행 갱신해도 OK
        when(mapper.markRead(10L, ADMIN_ID)).thenReturn(0);
        service.markRead(10L, ADMIN_ID);
    }

    @Test
    @DisplayName("markRead: 타 관리자 소유 알림 시도 시 NotFound 예외 발생 (AC-NC-002-3, REQ-NC-010)")
    void markRead_otherUserThrows() {
        when(mapper.findByIdAndUser(eq(99L), eq(ADMIN_ID))).thenReturn(null);

        assertThatThrownBy(() -> service.markRead(99L, ADMIN_ID))
                .isInstanceOf(AdminNotificationNotFoundException.class);

        verify(mapper, never()).markRead(any(), any());
    }

    // ─── REQ-NC-003 / AC-NC-003 ────────────────────────────────────────────

    @Test
    @DisplayName("markAllRead: 필터 없으면 전체 UNREAD 대상 (AC-NC-003-1)")
    void markAllRead_noFilter() {
        when(mapper.markAllRead(anyMap())).thenReturn(30);

        int updated = service.markAllRead(ADMIN_ID, new MarkAllReadRequest(null, null));

        assertThat(updated).isEqualTo(30);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).markAllRead(captor.capture());
        Map<String, Object> p = captor.getValue();
        assertThat(p.get("adminUserId")).isEqualTo(ADMIN_ID);
        assertThat(p.get("severityList")).isNull();
        assertThat(p.get("typeList")).isNull();
    }

    @Test
    @DisplayName("markAllRead: severity 필터 적용 시 매퍼에 전달된다 (AC-NC-003-2)")
    void markAllRead_severityFilter() {
        when(mapper.markAllRead(anyMap())).thenReturn(20);

        int updated = service.markAllRead(ADMIN_ID,
                new MarkAllReadRequest(List.of("ERROR"), null));

        assertThat(updated).isEqualTo(20);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).markAllRead(captor.capture());
        assertThat(captor.getValue().get("severityList")).asInstanceOf(list(String.class)).containsExactly("ERROR");
    }

    // ─── REQ-NC-004 / AC-NC-004 ────────────────────────────────────────────

    @Test
    @DisplayName("archive: 본인 소유 알림은 정상 보관된다 (AC-NC-004-1)")
    void archive_success() {
        when(mapper.findByIdAndUser(eq(10L), eq(ADMIN_ID))).thenReturn(sample(10L, "READ"));
        when(mapper.markArchived(10L, ADMIN_ID)).thenReturn(1);

        service.archive(10L, ADMIN_ID);

        verify(mapper).markArchived(10L, ADMIN_ID);
    }

    @Test
    @DisplayName("archive: 이미 ARCHIVED 인 경우 멱등 성공 (AC-NC-004 멱등성)")
    void archive_idempotent() {
        when(mapper.findByIdAndUser(eq(10L), eq(ADMIN_ID))).thenReturn(sample(10L, "ARCHIVED"));
        when(mapper.markArchived(10L, ADMIN_ID)).thenReturn(0);

        service.archive(10L, ADMIN_ID);
    }

    @Test
    @DisplayName("archive: 타 관리자 소유 시도 시 NotFound 발생 (REQ-NC-010)")
    void archive_otherUserThrows() {
        when(mapper.findByIdAndUser(eq(77L), eq(ADMIN_ID))).thenReturn(null);

        assertThatThrownBy(() -> service.archive(77L, ADMIN_ID))
                .isInstanceOf(AdminNotificationNotFoundException.class);

        verify(mapper, never()).markArchived(any(), any());
    }

    // ─── REQ-NC-005 / AC-NC-005 ────────────────────────────────────────────

    @Test
    @DisplayName("getUnreadCount: 매퍼 카운트를 그대로 반환한다 (AC-NC-005-1)")
    void getUnreadCount_returnsCount() {
        when(mapper.countUnread(ADMIN_ID)).thenReturn(7L);

        long count = service.getUnreadCount(ADMIN_ID);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    @DisplayName("getUnreadCount: 0건도 정상 반환한다 (AC-NC-005-2)")
    void getUnreadCount_zero() {
        when(mapper.countUnread(ADMIN_ID)).thenReturn(0L);

        assertThat(service.getUnreadCount(ADMIN_ID)).isEqualTo(0L);
    }
}
