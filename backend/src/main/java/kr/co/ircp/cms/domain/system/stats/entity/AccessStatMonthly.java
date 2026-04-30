package kr.co.ircp.cms.domain.system.stats.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 월별 접속 통계 엔티티.
 *
 * <p>REQ-SYSTEM-003-D — MonthlyStatsBatchJob이 매월 1일 02:00 집계.
 * top_pages / top_referrers / top_browsers는 JSONB 컬럼으로 저장.
 */
@Getter
@Builder
public class AccessStatMonthly {

    /** YYYY-MM 형식 */
    private String statMonth;
    private Long siteId;
    private Integer totalVisits;
    private Integer uniqueVisitors;
    private Integer pageViews;
    private Integer avgResponseMs;
    private Integer errorCount;
    /** [{page_url, count}] Top 10 */
    private List<Map<String, Object>> topPages;
    /** [{referrer, count}] Top 10 */
    private List<Map<String, Object>> topReferrers;
    /** [{browser, count}] Top 10 */
    private List<Map<String, Object>> topBrowsers;
}
