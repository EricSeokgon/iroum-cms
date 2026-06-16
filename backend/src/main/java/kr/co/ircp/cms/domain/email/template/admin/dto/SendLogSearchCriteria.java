package kr.co.ircp.cms.domain.email.template.admin.dto;

import java.time.Instant;

/**
 * 발송 로그 검색 조건 (REQ-ET-051) — 템플릿/상태/기간 필터 + 페이지네이션.
 *
 * @param templateId 템플릿 ID 필터(null=전체)
 * @param status     SUCCESS|FAILED 필터(null=전체)
 * @param from       조회 시작 시각(null=무제한)
 * @param to         조회 종료 시각(null=무제한)
 * @param page       0-base 페이지 번호
 * @param size       페이지 크기
 */
public record SendLogSearchCriteria(
        Long templateId,
        String status,
        Instant from,
        Instant to,
        int page,
        int size) {

    public int offset() {
        return Math.max(0, page) * effectiveSize();
    }

    public int effectiveSize() {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
