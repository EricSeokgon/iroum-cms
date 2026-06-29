package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;
import kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PostHistoryService RED/GREEN 테스트.
 * SPEC-CMS-POST-HISTORY-001 REQ-PH-001/002/003/004/005
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostHistoryService 테스트 (SPEC-CMS-POST-HISTORY-001)")
class PostHistoryServiceTest {

    @Mock
    private BbsPostHistoryMapper bbsPostHistoryMapper;

    @Mock
    private kr.co.ircp.cms.domain.board.repository.BbsPostMapper bbsPostMapper;

    private PostHistoryService postHistoryService;

    @BeforeEach
    void setUp() {
        postHistoryService = new PostHistoryServiceImpl(
                bbsPostHistoryMapper, new kr.co.ircp.cms.common.util.LineDiffCalculator(), bbsPostMapper);
    }

    // ── AC-PH-001/002: 페이징 목록을 version DESC 메타데이터로 반환 ────────────────

    @Test
    @DisplayName("AC-PH-001/002: getHistory는 페이징된 스냅샷 목록(version DESC 메타)을 반환한다")
    void getHistory_returnsPaginatedItems() {
        Instant now = Instant.now();
        PostHistoryItem v2 = new PostHistoryItem(20L, 2, "관리자", "오타 수정", now);
        PostHistoryItem v1 = new PostHistoryItem(10L, 1, "작성자", "최초 작성", now.minusSeconds(60));
        when(bbsPostHistoryMapper.findPageByPostId(eq(7L), anyInt(), anyInt()))
                .thenReturn(List.of(v2, v1));
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(2L);

        PageResponse<PostHistoryItem> result = postHistoryService.getHistory(7L, 0, 20);

        assertThat(result.content()).containsExactly(v2, v1);
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
    }

    // ── AC-PH-006: 이력이 없는 게시글은 빈 목록을 반환한다(오류 없음) ──────────────

    @Test
    @DisplayName("AC-PH-006: 이력이 없으면 빈 목록(totalElements=0)을 반환한다")
    void getHistory_noHistory_returnsEmptyList() {
        when(bbsPostHistoryMapper.findPageByPostId(eq(99L), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(bbsPostHistoryMapper.countByPostId(99L)).thenReturn(0L);

        PageResponse<PostHistoryItem> result = postHistoryService.getHistory(99L, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    // ── AC-PH-003: 삭제된 수정자 → editorName null 안전 처리 ──────────────────────

    @Test
    @DisplayName("AC-PH-003: edited_by가 NULL/삭제 사용자면 editorName이 null이어도 항목을 포함한다")
    void getHistory_deletedEditor_editorNameNull() {
        PostHistoryItem orphan = new PostHistoryItem(30L, 3, null, "사유", Instant.now());
        when(bbsPostHistoryMapper.findPageByPostId(eq(7L), anyInt(), anyInt()))
                .thenReturn(List.of(orphan));
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(1L);

        PageResponse<PostHistoryItem> result = postHistoryService.getHistory(7L, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).editorName()).isNull();
    }

    // ── AC-PH-001: offset 계산이 page*size 로 매퍼에 전달된다 ─────────────────────

    @Test
    @DisplayName("AC-PH-001: 두 번째 페이지 요청 시 offset=page*size 로 매퍼를 호출한다")
    void getHistory_secondPage_passesCorrectOffset() {
        when(bbsPostHistoryMapper.findPageByPostId(7L, 40, 20)).thenReturn(List.of());
        when(bbsPostHistoryMapper.countByPostId(7L)).thenReturn(50L);

        PageResponse<PostHistoryItem> result = postHistoryService.getHistory(7L, 2, 20);

        assertThat(result.totalElements()).isEqualTo(50L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    // ── AC-PH-004: 단건 버전 조회는 title + contentHtml 전체 본문을 반환한다 ──────

    @Test
    @DisplayName("AC-PH-004: getVersion은 해당 버전의 title + contentHtml 전체 본문을 반환한다")
    void getVersion_returnsFullContent() {
        PostHistoryDetail detail = new PostHistoryDetail(
                10L, 1, "관리자", "최초 작성", Instant.now(),
                "옛 제목", "<p>옛 본문</p>");
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 1))
                .thenReturn(Optional.of(detail));

        PostHistoryDetail result = postHistoryService.getVersion(7L, 1);

        assertThat(result.title()).isEqualTo("옛 제목");
        assertThat(result.contentHtml()).isEqualTo("<p>옛 본문</p>");
        assertThat(result.version()).isEqualTo(1);
    }

    // ── AC-PH-005: 존재하지 않는 버전 → PostHistoryVersionNotFoundException(404) ──

    @Test
    @DisplayName("AC-PH-005: 존재하지 않는 (postId, version)은 PostHistoryVersionNotFoundException")
    void getVersion_unknownVersion_throws() {
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 999))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postHistoryService.getVersion(7L, 999))
                .isInstanceOf(PostHistoryVersionNotFoundException.class);
    }
}
