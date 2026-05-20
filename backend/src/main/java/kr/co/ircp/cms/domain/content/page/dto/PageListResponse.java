package kr.co.ircp.cms.domain.content.page.dto;

import java.util.List;

/**
 * 페이지 목록 페이징 응답 DTO.
 * 프론트엔드 PageResponse<T> 인터페이스와 필드명 일치: content, number, size, totalElements, totalPages
 */
public record PageListResponse(
        List<PageResponse> content,
        int number,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageListResponse of(List<PageResponse> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageListResponse(content, page, size, total, totalPages);
    }
}
