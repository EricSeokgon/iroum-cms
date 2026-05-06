package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시판별 월별 통계.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001: BoardStatsMonthlyJob이 board_stats_daily를 월 합산.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardStatsMonthly {

    /** YYYY-MM */
    private String statMonth;
    private Long boardId;
    private Integer totalViews;
    private Integer uniqueVisitors;
    private Integer postCount;
    private Integer commentCount;
    private Instant aggregatedAt;
}
