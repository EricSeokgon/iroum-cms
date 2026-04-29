package kr.co.ircp.cms.domain.auth.dto;

import java.util.List;

/**
 * 페이징 응답 공통 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — GET /api/v1/users 페이징 응답에 사용.
 *
 * @param <T> 콘텐츠 항목 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 페이지 응답 빌더 헬퍼.
     *
     * @param content       현재 페이지 항목 목록
     * @param page          현재 페이지 번호 (0-based)
     * @param size          페이지 크기
     * @param totalElements 전체 항목 수
     * @return PageResponse 인스턴스
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
