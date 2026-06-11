package kr.co.ircp.cms.domain.notification.stat.dto;

import java.math.BigDecimal;

/**
 * 발송 현황 요약 응답 (today/7일/30일 3구간).
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-001 — 각 구간별 총 발송 건수, 읽음율(소수 2자리),
 * 미읽음 건수, 오류 건수.
 */
public record NotificationStatSummary(
        long todayDispatched, BigDecimal todayReadRate, long todayUnread, long todayErrors,
        long sevenDayDispatched, BigDecimal sevenDayReadRate, long sevenDayUnread, long sevenDayErrors,
        long thirtyDayDispatched, BigDecimal thirtyDayReadRate, long thirtyDayUnread, long thirtyDayErrors
) {}
