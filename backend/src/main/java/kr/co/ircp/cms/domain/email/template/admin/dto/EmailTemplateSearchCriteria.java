package kr.co.ircp.cms.domain.email.template.admin.dto;

/**
 * 이메일 템플릿 목록 검색 조건 (REQ-ET-003).
 *
 * @param templateType 템플릿 유형 필터(null=전체)
 * @param language     언어 필터(null=전체)
 * @param isActive     활성 여부 필터(null=전체)
 * @param keyword      code/name LIKE 키워드(null=전체)
 * @param page         0-base 페이지 번호
 * @param size         페이지 크기
 */
public record EmailTemplateSearchCriteria(
        String templateType,
        String language,
        Boolean isActive,
        String keyword,
        int page,
        int size) {

    /** LIMIT/OFFSET 계산용 offset. */
    public int offset() {
        return Math.max(0, page) * effectiveSize();
    }

    /** 1~100 범위로 클램프된 페이지 크기. */
    public int effectiveSize() {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
