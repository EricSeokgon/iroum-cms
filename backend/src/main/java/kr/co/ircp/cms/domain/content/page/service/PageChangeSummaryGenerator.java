package kr.co.ircp.cms.domain.content.page.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 페이지 변경 요약 자동 생성 유틸리티.
 *
 * <p>updatePage() 호출 시 changeSummary가 비어 있으면 변경된 필드명 목록을
 * 자동으로 생성한다. REQ-PHIST-003.
 *
 * // @MX:NOTE: [AUTO] REQ-PHIST-003 — 순수 정적 유틸. 부수효과 없음.
 * // @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001
 */
public final class PageChangeSummaryGenerator {

    private PageChangeSummaryGenerator() {
    }

    /**
     * 변경된 필드 목록을 사람이 읽을 수 있는 요약 문자열로 생성한다.
     *
     * @return 변경 항목이 없으면 "수정", 있으면 콤마로 연결된 변경 요약
     */
    public static String generate(String oldTitle, String newTitle, String oldSlug, String newSlug) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(oldTitle, newTitle)) {
            changed.add("제목 변경");
        }
        if (!Objects.equals(oldSlug, newSlug)) {
            changed.add("slug 변경");
        }
        return changed.isEmpty() ? "수정" : String.join(", ", changed);
    }
}
