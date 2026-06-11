package kr.co.ircp.cms.domain.notification.stat.dto;

/**
 * 일별 발송 추이 시계열 포인트.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-003 — 일자(YYYY-MM-DD)별 발송 건수·읽음 건수.
 */
public record DailyTrendPoint(String date, long dispatched, long readCount) {}
