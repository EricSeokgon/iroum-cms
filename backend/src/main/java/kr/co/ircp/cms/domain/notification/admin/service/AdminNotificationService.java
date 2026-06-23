package kr.co.ircp.cms.domain.notification.admin.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.admin.dto.AdminNotificationDto;
import kr.co.ircp.cms.domain.notification.admin.dto.MarkAllReadRequest;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;
import kr.co.ircp.cms.domain.notification.admin.event.AdminNotificationCreatedEvent;
import kr.co.ircp.cms.domain.notification.admin.exception.AdminNotificationNotFoundException;
import kr.co.ircp.cms.domain.notification.admin.repository.AdminNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 알림 받은편지함 서비스.
 *
 * <p>SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-001~005, 010 — 권한 격리·멱등 전이·페이지네이션.
 */
// @MX:NOTE: [AUTO] AdminNotificationService — Controller 단일 호출자, 향후 발송 인프라(SPEC-CMS-007) 연계 INSERT 도 본 클래스 경유
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationService {

    /** REQ-NC-001 페이지 사이즈 상한 — UI 무한 스크롤 남용 방지. */
    static final int MAX_PAGE_SIZE = 100;
    /** REQ-NC-001 기본 페이지 사이즈. */
    static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminNotificationMapper mapper;
    /** SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-002 — 알림 생성 시 WebSocket 푸시 이벤트 발행. */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-002 — 신규 관리자 알림 생성(저장 + 실시간 푸시 이벤트 발행).
     *
     * <p>알림을 {@code admin_notification} 에 INSERT 한 뒤
     * {@link AdminNotificationCreatedEvent} 를 발행한다.
     * {@code AdminNotificationWebSocketPublisher} 가 이를 구독하여 대상 관리자에게 STOMP 푸시한다.
     * 푸시는 트랜잭션 커밋 이후(AFTER_COMMIT) 수행되므로 저장과 알림 전송의 일관성이 보장된다.
     *
     * <p>기존 알림 생성 경로(매퍼 직접 INSERT)는 변경하지 않는다. 실시간 푸시가 필요한 신규
     * 호출만 본 메서드를 사용한다(additive).
     *
     * @param notification 저장 대상 알림(id 미설정 — MyBatis useGeneratedKeys 로 채워짐)
     * @return id 가 채워진 저장 완료 엔티티
     */
    @Transactional
    public AdminNotification insert(AdminNotification notification) {
        mapper.insert(notification);
        eventPublisher.publishEvent(new AdminNotificationCreatedEvent(notification));
        return notification;
    }

    /**
     * REQ-NC-001 — 본인 알림 목록을 페이지네이션·필터로 조회한다.
     */
    public PageResponse<AdminNotificationDto> getNotifications(
            Long adminUserId,
            List<String> statusList,
            List<String> severityList,
            List<String> typeList,
            Instant from,
            Instant to,
            int page,
            int size) {

        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);

        Map<String, Object> params = new HashMap<>();
        params.put("adminUserId", adminUserId);
        params.put("statusList", isEmpty(statusList) ? null : statusList);
        params.put("severityList", isEmpty(severityList) ? null : severityList);
        params.put("typeList", isEmpty(typeList) ? null : typeList);
        params.put("from", from);
        params.put("to", to);
        params.put("offset", safePage * safeSize);
        params.put("size", safeSize);

        List<AdminNotification> rows = mapper.findFiltered(params);
        long total = mapper.countFiltered(params);

        List<AdminNotificationDto> content = rows.stream()
                .map(AdminNotificationDto::from)
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    /**
     * REQ-NC-002 — 단건 읽음 처리. 본인 소유가 아니면 NotFound.
     */
    @Transactional
    public void markRead(Long id, Long adminUserId) {
        ensureOwned(id, adminUserId);
        mapper.markRead(id, adminUserId);
    }

    /**
     * REQ-NC-003 — 일괄 읽음 처리. 갱신된 행 수 반환.
     */
    @Transactional
    public int markAllRead(Long adminUserId, MarkAllReadRequest req) {
        Map<String, Object> params = new HashMap<>();
        params.put("adminUserId", adminUserId);
        if (req != null) {
            params.put("severityList", isEmpty(req.severity()) ? null : req.severity());
            params.put("typeList", isEmpty(req.type()) ? null : req.type());
        } else {
            params.put("severityList", null);
            params.put("typeList", null);
        }
        return mapper.markAllRead(params);
    }

    /**
     * REQ-NC-004 — 보관 처리. UNREAD 직접 보관 시 read_at 도 채워진다(매퍼 SQL).
     */
    @Transactional
    public void archive(Long id, Long adminUserId) {
        ensureOwned(id, adminUserId);
        mapper.markArchived(id, adminUserId);
    }

    /**
     * REQ-NC-005 — 본인 미읽음 수.
     */
    public long getUnreadCount(Long adminUserId) {
        return mapper.countUnread(adminUserId);
    }

    // ─── private helpers ───────────────────────────────────────────────────

    private void ensureOwned(Long id, Long adminUserId) {
        AdminNotification existing = mapper.findByIdAndUser(id, adminUserId);
        if (existing == null) {
            // REQ-NC-010 — 권한 격리: 존재 자체 enumeration 방지 위해 통합 예외 사용
            throw new AdminNotificationNotFoundException(id);
        }
    }

    private static int clampSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
