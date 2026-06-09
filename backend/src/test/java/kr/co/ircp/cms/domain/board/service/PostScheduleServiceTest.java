package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostScheduleRequest;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostScheduleConflictException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostI18nMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostService 예약 발행 RED/GREEN 테스트.
 * SPEC-CMS-POST-SCHEDULE-001 REQ-POST-SCHEDULE-001/002/004/007
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService 예약 발행 테스트 (SPEC-CMS-POST-SCHEDULE-001)")
class PostScheduleServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsPostHistoryMapper bbsPostHistoryMapper;
    @Mock private BbsViewLogMapper bbsViewLogMapper;
    @Mock private BbsPostI18nMapper bbsPostI18nMapper;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                bbsMasterMapper, bbsPostMapper, bbsPostHistoryMapper, bbsViewLogMapper,
                bbsPostI18nMapper, new HtmlSanitizer(), new AuthorizationGuard()
        );
    }

    private BbsPost stubPost(long id, String status) {
        return BbsPost.builder().id(id).bbsId(1L).authorId(1L)
                .title("제목").contentHtml("<p>내용</p>").contentText("내용")
                .status(status).viewCount(0).commentCount(0).build();
    }

    private BbsMaster stubMaster() {
        return BbsMaster.builder().id(1L).code("NOTICE").name("공지사항")
                .type("NOTICE").status("ACTIVE").useComment(true).build();
    }

    // ── AC-PS-001: 미래 시각 예약 → status=SCHEDULED + scheduled_at 저장 ──────────

    @Test
    @DisplayName("AC-PS-001: 미래 시각 예약 시 status=SCHEDULED 로 전환하고 mapper.schedule 호출")
    void schedulePost_futureTime_setsScheduled() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, "DRAFT")));
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster()));

        PostDetail result = postService.schedulePost(1L, new PostScheduleRequest(future));

        verify(bbsPostMapper).schedule(eq(1L), eq(future));
        assertThat(result.status()).isEqualTo("SCHEDULED");
    }

    // ── AC-PS-002: 과거 시각 예약 → 400 (IllegalArgumentException), 상태 미변경 ───

    @Test
    @DisplayName("AC-PS-002: 과거 시각 예약은 IllegalArgumentException, mapper.schedule 미호출")
    void schedulePost_pastTime_throwsAndNoStateChange() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, "DRAFT")));

        assertThatThrownBy(() -> postService.schedulePost(1L, new PostScheduleRequest(past)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(bbsPostMapper, never()).schedule(anyLong(), any());
    }

    // ── AC-PS-010: 존재하지 않는 게시글 예약 → 404 ──────────────────────────────

    @Test
    @DisplayName("AC-PS-010: 존재하지 않는 게시글 예약은 PostNotFoundException")
    void schedulePost_notFound_throws() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        when(bbsPostMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.schedulePost(99L, new PostScheduleRequest(future)))
                .isInstanceOf(PostNotFoundException.class);
    }

    // ── REQ-POST-SCHEDULE-007-2: DELETED 게시글 예약 → 409 ──────────────────────

    @Test
    @DisplayName("REQ-007-2: DELETED 게시글 예약은 PostScheduleConflictException(409)")
    void schedulePost_deleted_throwsConflict() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, "DELETED")));

        assertThatThrownBy(() -> postService.schedulePost(1L, new PostScheduleRequest(future)))
                .isInstanceOf(PostScheduleConflictException.class);

        verify(bbsPostMapper, never()).schedule(anyLong(), any());
    }

    // ── AC-PS-006: 예약 취소 → DRAFT 복귀 ────────────────────────────────────────

    @Test
    @DisplayName("AC-PS-006: SCHEDULED 예약 취소 시 clearSchedule 호출하고 status=DRAFT 반환")
    void cancelSchedule_scheduled_revertsToDraft() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, "SCHEDULED")));
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster()));

        PostDetail result = postService.cancelSchedule(1L);

        verify(bbsPostMapper).clearSchedule(1L);
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    // ── AC-PS-007: 비SCHEDULED 취소 → 409 ───────────────────────────────────────

    @Test
    @DisplayName("AC-PS-007: SCHEDULED 가 아닌 게시글 취소는 PostScheduleConflictException(409)")
    void cancelSchedule_notScheduled_throwsConflict() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, "PUBLISHED")));

        assertThatThrownBy(() -> postService.cancelSchedule(1L))
                .isInstanceOf(PostScheduleConflictException.class);

        verify(bbsPostMapper, never()).clearSchedule(anyLong());
    }

    @Test
    @DisplayName("취소 대상 게시글 미존재는 PostNotFoundException(404)")
    void cancelSchedule_notFound_throws() {
        when(bbsPostMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.cancelSchedule(99L))
                .isInstanceOf(PostNotFoundException.class);
    }
}
