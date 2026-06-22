package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 페이지 이력 보존 배치 단위 테스트.
 * REQ-PHIST-001 / AC-PHIST-003
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PageHistoryRetentionJob 단위 테스트")
class PageHistoryRetentionJobTest {

    @Mock
    PageHistoryMapper pageHistoryMapper;

    @InjectMocks
    PageHistoryRetentionJob job;

    @Test
    @DisplayName("AC-PHIST-003: 초과 이력 있는 페이지에 deleteOldestExceedingLimit 호출")
    void run_callsDelete_whenExceedsLimit() {
        job.setMaxVersions(50);
        when(pageHistoryMapper.findPageIdsWithExcessHistory(50)).thenReturn(List.of(1L, 2L));

        job.run();

        verify(pageHistoryMapper).deleteOldestExceedingLimit(1L, 50);
        verify(pageHistoryMapper).deleteOldestExceedingLimit(2L, 50);
    }

    @Test
    @DisplayName("초과 이력 없으면 delete 미호출")
    void run_noDelete_whenNoExcess() {
        job.setMaxVersions(50);
        when(pageHistoryMapper.findPageIdsWithExcessHistory(50)).thenReturn(List.of());

        job.run();

        verify(pageHistoryMapper, never()).deleteOldestExceedingLimit(any(), anyInt());
    }
}
