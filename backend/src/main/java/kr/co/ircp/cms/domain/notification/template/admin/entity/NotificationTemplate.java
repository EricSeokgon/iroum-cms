package kr.co.ircp.cms.domain.notification.template.admin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 알림 템플릿 엔티티 (notification_template).
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — V16 stub을 정식 CRUD 대상으로 승격.
 * variables는 JSONB 원문(String)으로 보관한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    private Long id;
    private String code;
    private String name;
    /** EMAIL / INAPP / KAKAO / SMS 등 — V16 stub 컬럼(이제 nullable). */
    private String channel;
    /** V16 stub 컬럼 (이제 nullable). */
    private String bodyTemplate;
    /** V16 stub 컬럼 — DRAFT 등. */
    private String reviewStatus;
    private String subject;
    private String bodyHtml;
    /** 필수 변수 정의 JSONB 원문. */
    private String variables;
    private String language;
    private Boolean isActive;
    private Long emailTemplateId;
    private Long createdBy;
    private Long updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
