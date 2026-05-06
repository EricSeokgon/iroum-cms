package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.CacheInvalidateRequest;
import kr.co.ircp.cms.domain.dashboard.dto.CacheStatsResponse;
import kr.co.ircp.cms.domain.dashboard.repository.ChartDatasetCacheMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CacheAdminService 단위 테스트.
 * REQ-VIZ-005-D-5 (캐시 무효화)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheAdminService — 캐시 무효화 + 통계 (REQ-VIZ-005-D-5)")
class CacheAdminServiceTest {

    @Mock private ChartDatasetCacheMapper cacheMapper;

    private CacheAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CacheAdminServiceImpl(cacheMapper);
    }

    @Test
    @DisplayName("invalidate — widgetIds 지정 시 widget 단위 expires_at = NOW()")
    void invalidate_byWidgetIds() {
        CacheInvalidateRequest req = new CacheInvalidateRequest(
                List.of(1L, 2L), null, null);

        service.invalidate(req);

        verify(cacheMapper, times(1)).expireByWidgetIds(eq(List.of(1L, 2L)));
        verify(cacheMapper, never()).expireAll();
    }

    @Test
    @DisplayName("invalidate — all=true 시 전체 만료")
    void invalidate_all() {
        CacheInvalidateRequest req = new CacheInvalidateRequest(null, null, true);

        service.invalidate(req);

        verify(cacheMapper, times(1)).expireAll();
    }

    @Test
    @DisplayName("stats — active/expired count 반환")
    void stats_returnsCounts() {
        when(cacheMapper.countActive()).thenReturn(120L);
        when(cacheMapper.countExpired()).thenReturn(35L);

        CacheStatsResponse resp = service.stats();

        assertThat(resp.activeEntries()).isEqualTo(120L);
        assertThat(resp.expiredEntries()).isEqualTo(35L);
    }
}
