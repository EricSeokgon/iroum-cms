package kr.co.ircp.cms.domain.system.stats.batch;

import kr.co.ircp.cms.domain.system.stats.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DailyStatsBatchJob GREEN 테스트.
 * REQ-SYSTEM-002-D: 일별 통계 배치 + 재시도 로직
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DailyStatsBatchJob GREEN 테스트 (REQ-SYSTEM-002-D)")
class DailyStatsBatchJobTest {

    @Mock private StatsService statsService;

    private DailyStatsBatchJob job;

    @BeforeEach
    void setUp() {
        job = new DailyStatsBatchJob(statsService);
    }

    @Test
    @DisplayName("runDaily() — 전일 날짜로 aggregateDaily 호출")
    void runDaily_calls_aggregateDaily_with_yesterday() {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);
        doNothing().when(statsService).aggregateDaily(any(), eq(1L));

        // when
        job.runDaily();

        // then
        verify(statsService).aggregateDaily(yesterday, 1L);
    }

    @Test
    @DisplayName("runDaily() — aggregateDaily 호출 시 올바른 siteId(1L) 사용")
    void runDaily_uses_site_id_1() {
        // given
        doNothing().when(statsService).aggregateDaily(any(), eq(1L));

        // when
        job.runDaily();

        // then
        verify(statsService).aggregateDaily(any(), eq(1L));
        // siteId=2L로는 호출하지 않음
        verify(statsService, never()).aggregateDaily(any(), eq(2L));
    }

    @Test
    @DisplayName("runDaily() — 성공 시 aggregateDaily 정확히 1회 호출")
    void runDaily_calls_once_on_success() {
        // given
        doNothing().when(statsService).aggregateDaily(any(), anyLong());

        // when
        job.runDaily();

        // then
        verify(statsService, times(1)).aggregateDaily(any(), anyLong());
    }
}
