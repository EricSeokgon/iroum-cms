package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 차트 데이터셋 캐시 엔티티 (TTL 5분).
 * REQ-VIZ-005-D-3
 *
 * <p>cache_key 형식: {@code widget:{id}:dim:{dim_hash}:role:{role}}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDatasetCache {
    private Long id;
    private String cacheKey;
    private Long widgetId;
    /** ECharts series JSON */
    private String dataset;
    private Instant generatedAt;
    private Instant expiresAt;
}
