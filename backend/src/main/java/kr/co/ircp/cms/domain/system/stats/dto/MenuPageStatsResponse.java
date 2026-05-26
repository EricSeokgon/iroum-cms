package kr.co.ircp.cms.domain.system.stats.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메뉴별 방문 통계 응답 DTO.
 * REQ-SYSTEM-002-D — 페이지 URL별 방문 통계 (날짜 범위 필터, 페이지네이션)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MenuPageStatsResponse {
    private String pageUrl;
    private String menuName;
    private Long visitCount;
    private Long uniqueVisitors;
    private Long avgResponseMs;
    private Double errorRate;
}
