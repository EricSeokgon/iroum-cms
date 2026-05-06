package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 게시판별 일별 통계.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001: BoardStatsDailyJob이 access_log + bbs_post + bbs_comment를 board_id 차원 집계.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardStatsDaily {

    private LocalDate statDate;
    private Long boardId;
    private Integer totalViews;
    private Integer uniqueVisitors;
    private Integer postCount;
    private Integer commentCount;
    private Integer avgResponseMs;
    private Instant aggregatedAt;
}
