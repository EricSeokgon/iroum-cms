package kr.co.ircp.cms.domain.system.stats.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 인기 페이지 응답 DTO.
 *
 * <p>REQ-SYSTEM-002-D — 기간별 Top 10 페이지 조회
 * // @MX:NOTE: [AUTO] record → class 변환 — MyBatis setter 기반 매핑 필요
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TopPageResponse {
    private String pageUrl;
    private Long views;
    private Long avgResponseMs;
    private Double errorRate;
    private Integer rank;
}
