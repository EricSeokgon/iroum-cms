package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 콘텐츠 일별 조회수 통계.
 *
 * <p>SPEC-CMS-009 REQ-DATA-002: ContentViewStatsDailyJob이 access_log /contents/{id} 패턴 집계.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentViewStatsDaily {

    private LocalDate statDate;
    private Long contentId;
    private Integer viewCount;
    private Integer uniqueViewers;
    private Integer avgDwellSec;
    private Instant aggregatedAt;
}
