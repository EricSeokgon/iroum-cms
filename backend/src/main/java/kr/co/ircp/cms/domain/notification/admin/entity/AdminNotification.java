package kr.co.ircp.cms.domain.notification.admin.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 운영 알림 받은편지함 엔티티.
 *
 * <p>SPEC-CMS-NOTIFICATION-CENTER-001 §5.1 — admin_notification 테이블(V40) 매핑.
 * MyBatis 기반이며 JPA 어노테이션을 사용하지 않는다.
 */
// @MX:NOTE: [AUTO] admin_notification 테이블 1:1 매핑 POJO — 상태 전이는 Service 에서 관리
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotification {
    private Long id;
    /** 알림 수신 관리자 (users.id). */
    private Long adminUserId;
    /** 알림 유형 코드 (예: POST_APPROVAL_REQUEST). */
    private String type;
    /** INFO / WARN / ERROR. */
    private String severity;
    private String title;
    private String body;
    /** 딥링크 대상 리소스 유형 (예: POST). */
    private String refType;
    /** 딥링크 대상 리소스 PK. */
    private Long refId;
    /** UNREAD / READ / ARCHIVED. */
    private String status;
    private Instant readAt;
    private Instant archivedAt;
    private Instant createdAt;
}
