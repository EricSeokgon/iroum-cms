package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.common.dto.DiffType;
import kr.co.ircp.cms.common.dto.RevisionDiffResponse;
import kr.co.ircp.cms.common.util.LineDiffCalculator;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
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
import static org.mockito.Mockito.when;

/**
 * PostHistoryService.diff RED/GREEN 테스트.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003, AC-003-1/2) — 게시물 두 version의
 * title·content_html 라인 diff. 존재하지 않는 version은 404 예외.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostHistoryService.diff 테스트 (SPEC-CMS-CONTENT-REVISION-001 M2)")
class PostHistoryDiffServiceTest {

    @Mock
    private BbsPostHistoryMapper bbsPostHistoryMapper;

    private PostHistoryService postHistoryService;

    @BeforeEach
    void setUp() {
        postHistoryService = new PostHistoryServiceImpl(bbsPostHistoryMapper, new LineDiffCalculator(),
                org.mockito.Mockito.mock(kr.co.ircp.cms.domain.board.repository.BbsPostMapper.class));
    }

    private PostHistoryDetail detail(int version, String title, String contentHtml) {
        return new PostHistoryDetail(
                (long) version, version, "관리자", "사유", Instant.now(), title, contentHtml);
    }

    @Test
    @DisplayName("AC-003-1/2: title과 content 두 필드의 diff를 반환한다")
    void diff_validVersions_returnsTitleAndContentDiff() {
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2))
                .thenReturn(Optional.of(detail(2, "옛 제목", "a\nb")));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 3))
                .thenReturn(Optional.of(detail(3, "새 제목", "a\nb\nc")));

        List<RevisionDiffResponse> result = postHistoryService.diff(7L, 2, 3);

        assertThat(result).extracting(RevisionDiffResponse::field)
                .containsExactly("title", "content");
        assertThat(result).allSatisfy(r -> {
            assertThat(r.fromVersion()).isEqualTo(2);
            assertThat(r.toVersion()).isEqualTo(3);
        });
        // content 필드: 마지막 라인 "c"가 INSERT
        RevisionDiffResponse content = result.stream()
                .filter(r -> r.field().equals("content")).findFirst().orElseThrow();
        assertThat(content.lines()).anyMatch(l -> l.type() == DiffType.INSERT && "c".equals(l.text()));
    }

    @Test
    @DisplayName("AC-003: from version이 없으면 PostHistoryVersionNotFoundException")
    void diff_fromVersionNotFound_throwsNotFoundException() {
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postHistoryService.diff(7L, 99, 3))
                .isInstanceOf(PostHistoryVersionNotFoundException.class);
    }

    @Test
    @DisplayName("AC-003: to version이 없으면 PostHistoryVersionNotFoundException")
    void diff_toVersionNotFound_throwsNotFoundException() {
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2))
                .thenReturn(Optional.of(detail(2, "제목", "본문")));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postHistoryService.diff(7L, 2, 99))
                .isInstanceOf(PostHistoryVersionNotFoundException.class);
    }

    @Test
    @DisplayName("동일한 내용은 모든 라인이 EQUAL이다")
    void diff_sameContent_allEqual() {
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 2))
                .thenReturn(Optional.of(detail(2, "같은 제목", "x\ny")));
        when(bbsPostHistoryMapper.findByPostIdAndVersion(7L, 3))
                .thenReturn(Optional.of(detail(3, "같은 제목", "x\ny")));

        List<RevisionDiffResponse> result = postHistoryService.diff(7L, 2, 3);

        assertThat(result).allSatisfy(r ->
                assertThat(r.lines()).allMatch(l -> l.type() == DiffType.EQUAL));
    }
}
