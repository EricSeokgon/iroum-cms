package kr.co.ircp.cms.common.util;

import kr.co.ircp.cms.common.dto.DiffLine;
import kr.co.ircp.cms.common.dto.DiffType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LineDiffCalculator RED/GREEN 테스트.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003) — LCS 기반 라인 단위 diff 정확성.
 * 동일=EQUAL, 추가=INSERT, 삭제=DELETE, 노이즈 0(trailing newline 무시) 검증.
 */
@DisplayName("LineDiffCalculator 테스트 (SPEC-CMS-CONTENT-REVISION-001 M2)")
class LineDiffCalculatorTest {

    private final LineDiffCalculator calculator = new LineDiffCalculator();

    @Test
    @DisplayName("동일한 두 텍스트는 모든 라인이 EQUAL로 표기된다")
    void calculate_identical_allEqual() {
        List<DiffLine> result = calculator.calculate("line1\nline2", "line1\nline2");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(l -> l.type() == DiffType.EQUAL);
        assertThat(result.get(0)).extracting(DiffLine::oldLineNo, DiffLine::newLineNo, DiffLine::text)
                .containsExactly(1, 1, "line1");
        assertThat(result.get(1)).extracting(DiffLine::oldLineNo, DiffLine::newLineNo, DiffLine::text)
                .containsExactly(2, 2, "line2");
    }

    @Test
    @DisplayName("한 라인 추가 시 INSERT가 감지된다 (oldLineNo=null)")
    void calculate_addedLine_detectsInsert() {
        List<DiffLine> result = calculator.calculate("a\nb", "a\nb\nc");

        assertThat(result).hasSize(3);
        DiffLine inserted = result.get(2);
        assertThat(inserted.type()).isEqualTo(DiffType.INSERT);
        assertThat(inserted.oldLineNo()).isNull();
        assertThat(inserted.newLineNo()).isEqualTo(3);
        assertThat(inserted.text()).isEqualTo("c");
    }

    @Test
    @DisplayName("한 라인 삭제 시 DELETE가 감지된다 (newLineNo=null)")
    void calculate_removedLine_detectsDelete() {
        List<DiffLine> result = calculator.calculate("a\nb\nc", "a\nc");

        DiffLine deleted = result.stream()
                .filter(l -> l.type() == DiffType.DELETE)
                .findFirst()
                .orElseThrow();
        assertThat(deleted.text()).isEqualTo("b");
        assertThat(deleted.oldLineNo()).isEqualTo(2);
        assertThat(deleted.newLineNo()).isNull();
        // 나머지 라인(a, c)은 EQUAL
        assertThat(result).filteredOn(l -> l.type() == DiffType.EQUAL)
                .extracting(DiffLine::text)
                .containsExactly("a", "c");
    }

    @Test
    @DisplayName("두 텍스트가 모두 비어 있으면 빈 결과를 반환한다")
    void calculate_emptyStrings_returnsEmpty() {
        assertThat(calculator.calculate("", "")).isEmpty();
    }

    @Test
    @DisplayName("공통 라인이 전혀 없으면 전부 DELETE + INSERT로 치환 표기된다 (EQUAL 없음)")
    void calculate_completelyDifferent_allReplaced() {
        List<DiffLine> result = calculator.calculate("x\ny", "a\nb");

        assertThat(result).noneMatch(l -> l.type() == DiffType.EQUAL);
        assertThat(result).filteredOn(l -> l.type() == DiffType.DELETE)
                .extracting(DiffLine::text).containsExactly("x", "y");
        assertThat(result).filteredOn(l -> l.type() == DiffType.INSERT)
                .extracting(DiffLine::text).containsExactly("a", "b");
    }

    @Test
    @DisplayName("후행 개행(trailing newline) 차이는 노이즈를 만들지 않는다")
    void calculate_trailingNewline_noiseFree() {
        List<DiffLine> result = calculator.calculate("a\nb\n", "a\nb");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(l -> l.type() == DiffType.EQUAL);
    }
}
