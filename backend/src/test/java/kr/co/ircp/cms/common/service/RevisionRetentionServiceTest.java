package kr.co.ircp.cms.common.service;

import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.system.setting.entity.SystemSetting;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RevisionRetentionService RED/GREEN 테스트.
 * SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006) — 엔티티별 리비전 이력 최대 보존 개수.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RevisionRetentionService 테스트 (SPEC-CMS-CONTENT-REVISION-001 M3)")
class RevisionRetentionServiceTest {

    private static final String KEY = "content.revision.maxPerEntity";

    @Mock private SystemSettingMapper systemSettingMapper;
    @Mock private BbsPostHistoryMapper bbsPostHistoryMapper;
    @Mock private PageHistoryMapper pageHistoryMapper;

    private RevisionRetentionService retentionService;

    @BeforeEach
    void setUp() {
        retentionService = new RevisionRetentionServiceImpl(
                systemSettingMapper, bbsPostHistoryMapper, pageHistoryMapper);
    }

    private SystemSetting maxSetting(String value) {
        return SystemSetting.builder().key(KEY).value(value).valueType("INT").build();
    }

    // ── 게시물 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prunePostHistory — 이력 수가 최대치 이하면 삭제하지 않는다")
    void prunePostHistory_underLimit_doesNotDelete() {
        when(systemSettingMapper.findByKey(KEY)).thenReturn(Optional.of(maxSetting("50")));
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(10L);

        retentionService.prunePostHistory(7L);

        verify(bbsPostHistoryMapper, never()).deleteOldestByPostId(anyLong(), anyInt());
    }

    @Test
    @DisplayName("prunePostHistory — 이력 수가 최대치+1이면 최신 max개만 남기고 정리한다")
    void prunePostHistory_atLimit_deleteOldest() {
        when(systemSettingMapper.findByKey(KEY)).thenReturn(Optional.of(maxSetting("50")));
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(51L);

        retentionService.prunePostHistory(7L);

        verify(bbsPostHistoryMapper).deleteOldestByPostId(7L, 50);
    }

    @Test
    @DisplayName("prunePostHistory — 초과분이 여러 개여도 최신 max개만 남기고 정리한다")
    void prunePostHistory_overLimit_deletesAllExcess() {
        when(systemSettingMapper.findByKey(KEY)).thenReturn(Optional.of(maxSetting("50")));
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(53L);

        retentionService.prunePostHistory(7L);

        verify(bbsPostHistoryMapper).deleteOldestByPostId(7L, 50);
    }

    @Test
    @DisplayName("prunePostHistory — system_setting 미존재 시 기본값 50 적용")
    void prunePostHistory_settingNotFound_usesDefault50() {
        when(systemSettingMapper.findByKey(KEY)).thenReturn(Optional.empty());
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(51L);

        retentionService.prunePostHistory(7L);

        verify(bbsPostHistoryMapper).deleteOldestByPostId(7L, 50);
    }

    // ── 페이지 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prunePageHistory — 이력 수가 최대치 이하면 삭제하지 않는다")
    void prunePageHistory_underLimit_doesNotDelete() {
        when(systemSettingMapper.findByKey(KEY)).thenReturn(Optional.of(maxSetting("50")));
        when(pageHistoryMapper.countByPageId(3L)).thenReturn(10L);

        retentionService.prunePageHistory(3L);

        verify(pageHistoryMapper, never()).deleteOldestByPageId(anyLong(), anyInt());
    }

    @Test
    @DisplayName("prunePageHistory — 이력 수가 최대치+1이면 최신 max개만 남기고 정리한다")
    void prunePageHistory_atLimit_deleteOldest() {
        when(systemSettingMapper.findByKey(KEY)).thenReturn(Optional.of(maxSetting("50")));
        when(pageHistoryMapper.countByPageId(3L)).thenReturn(51L);

        retentionService.prunePageHistory(3L);

        verify(pageHistoryMapper).deleteOldestByPageId(3L, 50);
    }
}
