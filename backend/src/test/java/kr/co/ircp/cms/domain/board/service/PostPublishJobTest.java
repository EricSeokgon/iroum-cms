package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostPublishJob 배치 발행 RED/GREEN 테스트.
 * SPEC-CMS-POST-SCHEDULE-001 REQ-POST-SCHEDULE-003: 만기 예약 게시글 자동 발행.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublishJob 배치 테스트 (SPEC-CMS-POST-SCHEDULE-001)")
class PostPublishJobTest {

    @Mock private BbsPostMapper bbsPostMapper;
    @InjectMocks private PostPublishJob postPublishJob;

    private BbsPost duePost(long id) {
        return BbsPost.builder().id(id).bbsId(1L).status("SCHEDULED").build();
    }

    // ── AC-PS-004: 만기 게시글 → PUBLISHED 전환 ─────────────────────────────────

    @Test
    @DisplayName("AC-PS-004: 만기 예약 게시글마다 publishScheduled 호출")
    void publishDuePosts_publishesEachDuePost() {
        when(bbsPostMapper.findScheduledDue()).thenReturn(List.of(duePost(1L), duePost(2L)));

        postPublishJob.publishDuePosts();

        verify(bbsPostMapper).publishScheduled(1L);
        verify(bbsPostMapper).publishScheduled(2L);
    }

    // ── AC-PS-005: 만기 게시글 없으면 발행하지 않음 ──────────────────────────────

    @Test
    @DisplayName("AC-PS-005: 만기 게시글이 없으면 publishScheduled 미호출")
    void publishDuePosts_noDuePosts_doesNothing() {
        when(bbsPostMapper.findScheduledDue()).thenReturn(List.of());

        postPublishJob.publishDuePosts();

        verify(bbsPostMapper, never()).publishScheduled(org.mockito.ArgumentMatchers.anyLong());
    }
}
