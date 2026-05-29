package kr.co.ircp.cms.domain.notification.admin.dto;

import java.util.List;

/**
 * REQ-NC-003 — 일괄 읽음 처리 요청 본문 (선택 필터).
 *
 * <p>두 필드 모두 null/빈 리스트면 전체 UNREAD 가 대상이 된다.
 */
public record MarkAllReadRequest(
        List<String> severity,
        List<String> type
) {
}
