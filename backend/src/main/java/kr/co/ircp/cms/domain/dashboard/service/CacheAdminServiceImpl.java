package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.CacheInvalidateRequest;
import kr.co.ircp.cms.domain.dashboard.dto.CacheStatsResponse;
import kr.co.ircp.cms.domain.dashboard.repository.ChartDatasetCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 캐시 관리 서비스 구현.
 * REQ-VIZ-005-D-5
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CacheAdminServiceImpl implements CacheAdminService {

    private final ChartDatasetCacheMapper cacheMapper;

    @Override
    @Transactional
    public void invalidate(CacheInvalidateRequest req) {
        if (req.invalidateAll()) {
            cacheMapper.expireAll();
            return;
        }
        if (req.widgetIds() != null && !req.widgetIds().isEmpty()) {
            cacheMapper.expireByWidgetIds(req.widgetIds());
        }
        if (req.kpiIds() != null && !req.kpiIds().isEmpty()) {
            // 1차: kpi_id 기반은 dataset 본문에 kpi 가 들어있어 직접 매핑이 어려우므로
            // widget 단위 무효화로 위임. v0.4+ 에서 widget→kpi 역방향 인덱스 도입.
            for (Long ignore : req.kpiIds()) {
                // no-op : widget 기반 invalidate 사용 권장
            }
        }
    }

    @Override
    public CacheStatsResponse stats() {
        return new CacheStatsResponse(
                cacheMapper.countActive(),
                cacheMapper.countExpired());
    }
}
