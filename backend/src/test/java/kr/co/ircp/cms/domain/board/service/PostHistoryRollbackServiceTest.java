package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.common.exception.RevisionConflictException;
import kr.co.ircp.cms.common.util.LineDiffCalculator;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostHistoryService 롤백 RED/GREEN 테스트.
 * SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-007) — 게시물 특정 버전 롤백 + 낙관적 잠금.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostHistoryService 롤백 테스트 (SPEC-CMS-CONTENT-REVISION-001 M3)")
class PostHistoryRollbackServiceTest {

    @Mock private BbsPostHistoryMapper bbsPostHistoryMapper;
    @Mock private BbsPostMapper bbsPostMapper;

    private PostHistoryService postHistoryService;

    @BeforeEach
    void setUp() {
        postHistoryService = new PostHistoryServiceImpl(
                bbsPostHistoryMapper, new LineDiffCalculator(), bbsPostMapper);
    }

    private BbsPost currentPost(int version) {
        return BbsPost.builder().id(7L).bbsId(1L).authorId(1L)
                .title("현재 제목").contentHtml("<p>현재 본문</p>").contentText("현재 본문")
                .status("PUBLISHED").version(version).build();
    }

    private PostHistoryDetail snapshotV2() {
        return new PostHistoryDetail(
                2L, 2, "관리자", "v2 작성", Instant.now(), "v2 제목", "<p>v2 본문</p>");
    }

    @Test
    @DisplayName("rollback — 유효 버전이면 게시물 title·content_html을 해당 버전으로 복원한다")
    void rollback_validVersion_restoresTitleAndContent() {
        when(bbsPostMapper.findById(7L)).thenReturn(Optional.of(currentPost(5)));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2)).thenReturn(Optional.of(snapshotV2()));
        when(bbsPostHistoryMapper.nextVersionByPostId(7L)).thenReturn(6);
        when(bbsPostMapper.update(any())).thenReturn(1);

        PostHistoryDetail result = postHistoryService.rollback(7L, 2, 5);

        assertThat(result.title()).isEqualTo("v2 제목");
        assertThat(result.contentHtml()).isEqualTo("<p>v2 본문</p>");
    }

    @Test
    @DisplayName("rollback — 존재하지 않는 버전이면 PostHistoryVersionNotFoundException(404)")
    void rollback_versionNotFound_throwsNotFoundException() {
        when(bbsPostMapper.findById(7L)).thenReturn(Optional.of(currentPost(5)));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postHistoryService.rollback(7L, 999, 5))
                .isInstanceOf(PostHistoryVersionNotFoundException.class);
    }

    @Test
    @DisplayName("rollback — expectedVersion 불일치 시 RevisionConflictException(409) + currentVersion")
    void rollback_versionMismatch_throwsConflict() {
        when(bbsPostMapper.findById(7L)).thenReturn(Optional.of(currentPost(5)));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2)).thenReturn(Optional.of(snapshotV2()));

        assertThatThrownBy(() -> postHistoryService.rollback(7L, 2, 3))
                .isInstanceOf(RevisionConflictException.class)
                .extracting("currentVersion").isEqualTo(5L);
    }

    @Test
    @DisplayName("rollback — 롤백 자체가 새 이력 항목을 생성한다")
    void rollback_createsNewHistoryEntry() {
        when(bbsPostMapper.findById(7L)).thenReturn(Optional.of(currentPost(5)));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2)).thenReturn(Optional.of(snapshotV2()));
        when(bbsPostHistoryMapper.nextVersionByPostId(7L)).thenReturn(6);
        when(bbsPostMapper.update(any())).thenReturn(1);

        postHistoryService.rollback(7L, 2, 5);

        verify(bbsPostHistoryMapper).insert(any());
    }

    @Test
    @DisplayName("rollback — 롤백 후 version은 직전 version + 1 이다")
    void rollback_newVersionIsIncrementOfCurrent() {
        when(bbsPostMapper.findById(7L)).thenReturn(Optional.of(currentPost(5)));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2)).thenReturn(Optional.of(snapshotV2()));
        when(bbsPostHistoryMapper.nextVersionByPostId(7L)).thenReturn(6);
        when(bbsPostMapper.update(any())).thenReturn(1);

        PostHistoryDetail result = postHistoryService.rollback(7L, 2, 5);

        assertThat(result.version()).isEqualTo(6);
    }
}
