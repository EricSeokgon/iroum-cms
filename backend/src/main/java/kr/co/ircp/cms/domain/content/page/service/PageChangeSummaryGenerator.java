package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.dto.PageUpdateRequest;
import kr.co.ircp.cms.domain.content.page.entity.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 페이지 수정 시 변경 요약(changeSummary)을 자동 생성하는 순수 함수형 유틸.
 *
 * <p>SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-003 — diff 기반 자동 요약.
 * <ul>
 *   <li>사용자가 changeSummary를 명시하면(비공백) 그 값을 우선 사용한다.</li>
 *   <li>미입력(null/공백) 시 직전 page와 신규 request의 필드 차이를 비교하여
 *       "제목 변경, 슬러그 변경" 형태의 한국어 요약을 생성한다.</li>
 *   <li>변경된 필드가 없으면 기본 문구 "변경 없음"을 기록한다.</li>
 * </ul>
 *
 * <p>비교 대상 필드는 현재 snapshot에 포함된 title, slug로 한정한다(SPEC 제약).
 */
// @MX:NOTE: [AUTO] PageChangeSummaryGenerator — REQ-PHIST-003 자동 요약 생성 (순수 함수)
// @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001#REQ-PHIST-003
@Component
public class PageChangeSummaryGenerator {

    /** 변경 없음 기본 문구. */
    static final String NO_CHANGE = "변경 없음";

    /** 자동 요약 길이 상한 (SPEC 제약: 200자). */
    private static final int MAX_LENGTH = 200;

    /**
     * 페이지 수정 요약을 생성한다.
     *
     * @param current 수정 전 페이지(직전 상태)
     * @param request 페이지 수정 요청
     * @return 사용자 입력(비공백) 또는 diff 기반 자동 요약 또는 "변경 없음"
     */
    public String summarize(Page current, PageUpdateRequest request) {
        // 1) 사용자 입력 우선 (비공백)
        String userInput = request.changeSummary();
        if (userInput != null && !userInput.isBlank()) {
            return truncate(userInput.trim());
        }

        // 2) diff 기반 자동 생성
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(request.title(), current.getTitle())) {
            changes.add("제목 변경");
        }
        if (request.slug() != null && !Objects.equals(request.slug(), current.getSlug())) {
            changes.add("슬러그 변경");
        }

        // 3) 변경 없음 → 기본 문구
        if (changes.isEmpty()) {
            return NO_CHANGE;
        }
        return truncate(String.join(", ", changes));
    }

    private String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
