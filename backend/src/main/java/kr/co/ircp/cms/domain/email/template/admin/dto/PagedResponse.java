package kr.co.ircp.cms.domain.email.template.admin.dto;

import java.util.List;

/**
 * 페이지네이션 응답 래퍼.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 — 템플릿/발송로그 목록 조회 공통 응답.
 *
 * @param content    현재 페이지 항목
 * @param page       0-base 페이지 번호
 * @param size       페이지 크기
 * @param totalCount 전체 건수
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalCount) {

    /** 전체 페이지 수. */
    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / size);
    }
}
