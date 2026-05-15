package kr.co.ircp.cms.domain.system.stats.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 일별 접속 통계 엔티티.
 *
 * <p>REQ-SYSTEM-002-D — DailyStatsBatchJob이 매일 01:00 집계하여 UPSERT.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessStatDaily {

    private LocalDate statDate;
    private Long siteId;
    private Integer totalVisits;
    private Integer uniqueVisitors;
    private Integer uniqueSessions;
    private Integer pageViews;
    private Integer avgResponseMs;
    private Integer errorCount;
}
