package kr.co.ircp.cms.domain.content.page.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.common.dto.DiffType;
import kr.co.ircp.cms.common.dto.RevisionDiffResponse;
import kr.co.ircp.cms.common.util.LineDiffCalculator;
import kr.co.ircp.cms.domain.content.page.entity.PageHistory;
import kr.co.ircp.cms.domain.content.page.exception.PageHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.content.page.util.PageSnapshotFlattener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PageHistoryService.diff RED/GREEN 테스트.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003, AC-003-3) — 페이지 두 version의
 * title·slug diff. 게시물과 달리 slug 필드가 포함된다. 미존재 version은 404.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PageHistoryService.diff 테스트 (SPEC-CMS-CONTENT-REVISION-001 M2)")
class PageHistoryDiffServiceTest {

    @Mock
    private PageHistoryMapper pageHistoryMapper;

    private PageHistoryService pageHistoryService;

    @BeforeEach
    void setUp() {
        pageHistoryService = new PageHistoryServiceImpl(
                pageHistoryMapper,
                new PageSnapshotFlattener(new ObjectMapper()),
                new LineDiffCalculator());
    }

    private PageHistory history(int version, String snapshotJson) {
        return PageHistory.builder()
                .id((long) version)
                .pageId(3L)
                .version(version)
                .snapshot(snapshotJson)
                .build();
    }

    @Test
    @DisplayName("AC-003-3: 페이지 diff는 slug 필드를 포함한다")
    void diff_validVersions_returnsTitleAndSlugDiff() {
        when(pageHistoryMapper.findByPageIdAndVersion(3L, 1))
                .thenReturn(Optional.of(history(1, "{\"title\":\"옛 제목\",\"slug\":\"old-slug\"}")));
        when(pageHistoryMapper.findByPageIdAndVersion(3L, 2))
                .thenReturn(Optional.of(history(2, "{\"title\":\"새 제목\",\"slug\":\"new-slug\"}")));

        List<RevisionDiffResponse> result = pageHistoryService.diff(3L, 1, 2);

        assertThat(result).extracting(RevisionDiffResponse::field)
                .contains("slug", "title");
        RevisionDiffResponse slug = result.stream()
                .filter(r -> r.field().equals("slug")).findFirst().orElseThrow();
        assertThat(slug.lines()).anyMatch(l -> l.type() == DiffType.INSERT && "new-slug".equals(l.text()));
        assertThat(slug.lines()).anyMatch(l -> l.type() == DiffType.DELETE && "old-slug".equals(l.text()));
    }

    @Test
    @DisplayName("AC-003-3: slug가 동일하면 slug 필드 diff는 모두 EQUAL이다")
    void diff_sameSlug_slugFieldAllEqual() {
        when(pageHistoryMapper.findByPageIdAndVersion(3L, 1))
                .thenReturn(Optional.of(history(1, "{\"title\":\"제목A\",\"slug\":\"same-slug\"}")));
        when(pageHistoryMapper.findByPageIdAndVersion(3L, 2))
                .thenReturn(Optional.of(history(2, "{\"title\":\"제목B\",\"slug\":\"same-slug\"}")));

        List<RevisionDiffResponse> result = pageHistoryService.diff(3L, 1, 2);

        RevisionDiffResponse slug = result.stream()
                .filter(r -> r.field().equals("slug")).findFirst().orElseThrow();
        assertThat(slug.lines()).allMatch(l -> l.type() == DiffType.EQUAL);
    }

    @Test
    @DisplayName("AC-003: 미존재 version은 PageHistoryVersionNotFoundException")
    void diff_missingVersion_throwsNotFound() {
        when(pageHistoryMapper.findByPageIdAndVersion(3L, 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pageHistoryService.diff(3L, 99, 2))
                .isInstanceOf(PageHistoryVersionNotFoundException.class);
    }
}
