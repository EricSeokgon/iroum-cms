package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 콘텐츠 월별 조회수 통계.
 *
 * <p>SPEC-CMS-009 REQ-DATA-002: ContentViewStatsMonthlyJob이 일별 데이터를 월 합산.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentViewStatsMonthly {

    /** YYYY-MM */
    private String statMonth;
    private Long contentId;
    private Integer viewCount;
    private Integer uniqueViewers;
    private Instant aggregatedAt;
}
