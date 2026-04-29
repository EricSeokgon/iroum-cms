package kr.co.ircp.cms.domain.content.page.dto;

import kr.co.ircp.cms.domain.content.page.entity.PageHistory;

import java.time.Instant;

/**
 * 페이지 변경 이력 응답 DTO.
 * REQ-CONTENT-005-D-6: 이력 목록 (version desc)
 */
public record PageHistoryResponse(
        Long id,
        Long pageId,
        int version,
        String snapshot,
        Long editedBy,
        Instant editedAt,
        String changeSummary
) {
    public static PageHistoryResponse from(PageHistory history) {
        return new PageHistoryResponse(
                history.getId(),
                history.getPageId(),
                history.getVersion(),
                history.getSnapshot(),
                history.getEditedBy(),
                history.getEditedAt(),
                history.getChangeSummary()
        );
    }
}
