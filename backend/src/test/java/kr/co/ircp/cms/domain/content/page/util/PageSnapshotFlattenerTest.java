package kr.co.ircp.cms.domain.content.page.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PageSnapshotFlattener RED/GREEN 테스트.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003, AC-003-3/4) — 페이지 이력 JSONB
 * 스냅샷({@code {"title":...,"slug":...}})을 diff 비교용 필드 맵으로 평탄화한다.
 * 깨진 JSON·필드 누락은 예외 없이 안전 처리(노이즈 0)된다.
 */
@DisplayName("PageSnapshotFlattener 테스트 (SPEC-CMS-CONTENT-REVISION-001 M2)")
class PageSnapshotFlattenerTest {

    private final PageSnapshotFlattener flattener = new PageSnapshotFlattener(new ObjectMapper());

    @Test
    @DisplayName("정상 JSON에서 title과 slug를 추출한다")
    void flatten_validJson_extractsTitleAndSlug() {
        Map<String, String> result = flattener.flatten("{\"title\":\"T\",\"slug\":\"s\"}");

        assertThat(result).containsEntry("title", "T");
        assertThat(result).containsEntry("slug", "s");
    }

    @Test
    @DisplayName("키 순서가 달라도 동일한 결과를 반환한다 (안정성)")
    void flatten_keyOrderIndependent_sameResult() {
        Map<String, String> a = flattener.flatten("{\"title\":\"T\",\"slug\":\"s\"}");
        Map<String, String> b = flattener.flatten("{\"slug\":\"s\",\"title\":\"T\"}");

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("깨진 JSON은 예외 없이 빈 맵을 반환한다")
    void flatten_brokenJson_returnsEmpty() {
        assertThat(flattener.flatten("{not valid json")).isEmpty();
        assertThat(flattener.flatten(null)).isEmpty();
        assertThat(flattener.flatten("")).isEmpty();
    }

    @Test
    @DisplayName("일부 필드만 있으면 존재하는 필드만 반환한다")
    void flatten_missingField_returnsOnlyPresentField() {
        Map<String, String> result = flattener.flatten("{\"title\":\"T\"}");

        assertThat(result).containsOnlyKeys("title");
        assertThat(result).containsEntry("title", "T");
    }
}
