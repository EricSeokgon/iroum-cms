package kr.co.ircp.cms.domain.notification.admin.repository;

import java.util.List;
import java.util.Map;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 알림 받은편지함 MyBatis 매퍼.
 *
 * <p>SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-001~005, 010.
 * 모든 메서드는 admin_user_id 강제 주입으로 권한 격리(REQ-NC-010)를 보장한다.
 */
@Mapper
public interface AdminNotificationMapper {

    /**
     * REQ-NC-001 — 필터/페이지네이션 조회.
     *
     * <p>지원 파라미터(Map 키):
     * <ul>
     *   <li>adminUserId (Long, 필수)</li>
     *   <li>statusList (List&lt;String&gt;, optional) — null/empty 시 전체 상태</li>
     *   <li>severityList (List&lt;String&gt;, optional)</li>
     *   <li>typeList (List&lt;String&gt;, optional)</li>
     *   <li>from / to (Instant, optional)</li>
     *   <li>offset / size (int)</li>
     * </ul>
     */
    List<AdminNotification> findFiltered(Map<String, Object> params);

    /** REQ-NC-001 — findFiltered 와 동일 조건의 총건수. */
    long countFiltered(Map<String, Object> params);

    /** REQ-NC-005 — 본인 미읽음 알림 수. */
    long countUnread(@Param("adminUserId") Long adminUserId);

    /** REQ-NC-001/002 — 본인 알림 단건 조회 (권한 격리). */
    AdminNotification findByIdAndUser(@Param("id") Long id,
                                      @Param("adminUserId") Long adminUserId);

    /** REQ-NC-002 — 개별 읽음 처리. 갱신된 행 수 반환. */
    int markRead(@Param("id") Long id,
                 @Param("adminUserId") Long adminUserId);

    /** REQ-NC-003 — 일괄 읽음 처리. 갱신된 행 수 반환. */
    int markAllRead(Map<String, Object> params);

    /** REQ-NC-004 — 보관 처리 (UNREAD→ARCHIVED 시 read_at 도 채움). 갱신된 행 수 반환. */
    int markArchived(@Param("id") Long id,
                     @Param("adminUserId") Long adminUserId);

    /** 발송 인프라(SPEC-CMS-007) 연계용 INSERT. */
    void insert(AdminNotification notification);

    /**
     * 모든 ADMIN/SUPER_ADMIN 역할 사용자에게 운영 알림 일괄 INSERT (SPEC-CMS-SURVEY-001 REQ-SURVEY-012/013).
     *
     * <p>단일 INSERT...SELECT 로 user_roles JOIN roles 결과(ADMIN/SUPER_ADMIN)만큼 행을 생성한다.
     */
    void insertForAdminRoles(
            @Param("type") String type,
            @Param("severity") String severity,
            @Param("title") String title,
            @Param("body") String body,
            @Param("refId") Long refId
    );
}
