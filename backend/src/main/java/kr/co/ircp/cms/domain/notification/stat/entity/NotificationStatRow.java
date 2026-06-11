package kr.co.ircp.cms.domain.notification.stat.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * MyBatis 집계 결과 매핑용 행 (도메인 엔티티 아님).
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 — 요약/카테고리/일별/오류 4계열 쿼리 결과를
 * 단일 행 타입으로 받아 서비스단에서 DTO 로 변환한다.
 */
@Data
public class NotificationStatRow {

    /** 요약 구간 식별자: today/7d/30d. */
    private String period;
    /** 발송 건수. */
    private long dispatched;
    /** 읽음 건수. */
    private long readCount;
    /** 미읽음 건수. */
    private long unreadCount;
    /** 오류 건수 (delivery_status IN FAILED/PENDING). */
    private long errorCount;
    /** 읽음율(소수 2자리, 0~100). */
    private BigDecimal readRate;
    /** 카테고리용 type 코드. */
    private String type;
    /** 일별 추이용 일자 (YYYY-MM-DD). */
    private String statDate;

    // ─── 오류 목록(FailedNotification)용 ─────────────────────────────────────
    /** 알림 PK. */
    private Long id;
    /** 수신 사용자 PK. */
    private Long userId;
    /** 알림 제목. */
    private String title;
    /** 발송 상태 코드. */
    private String deliveryStatus;
    /** 생성 시각. */
    private java.time.Instant createdAt;
}
