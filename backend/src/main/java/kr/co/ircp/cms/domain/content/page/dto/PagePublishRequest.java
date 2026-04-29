package kr.co.ircp.cms.domain.content.page.dto;

/**
 * 페이지 즉시 발행 요청 DTO.
 * REQ-CONTENT-005-D-3: status=PUBLISHED, published_at=now
 */
public record PagePublishRequest(
        String changeSummary
) {}
