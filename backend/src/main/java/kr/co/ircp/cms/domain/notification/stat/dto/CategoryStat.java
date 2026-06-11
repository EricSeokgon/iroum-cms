package kr.co.ircp.cms.domain.notification.stat.dto;

/**
 * 카테고리(type)별 발송 통계 응답.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-002 — type 별 발송 건수와 읽음 건수.
 */
public record CategoryStat(String type, long dispatched, long readCount) {}
