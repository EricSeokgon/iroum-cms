package kr.co.ircp.cms.common.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import kr.co.ircp.cms.common.dto.DiffLine;
import kr.co.ircp.cms.common.dto.DiffType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LCS 기반 라인 단위 diff 계산기.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003) — java-diff-utils의 LCS 알고리즘으로
 * 두 텍스트를 라인 단위 비교하여 EQUAL/INSERT/DELETE 목록을 만든다. 게시물·페이지 양
 * 도메인이 공유하는 표현 계층 유틸 (plan §1.1).
 *
 * <p>치환(CHANGE)은 별도 유형 없이 이전 라인 DELETE + 이후 라인 INSERT 조합으로 표현한다.
 */
// @MX:ANCHOR: [AUTO] 게시물·페이지 diff 서비스 공용 진입점 (fan_in 2 — 추후 확장 예정)
// @MX:REASON: revision diff 정확성의 단일 진실. 라인 번호 규칙 변경 시 양 도메인 동시 영향.
@Component
public class LineDiffCalculator {

    /**
     * 두 텍스트의 라인 단위 diff를 계산한다.
     *
     * @param fromText 이전(from) version 텍스트 (null 허용)
     * @param toText   이후(to) version 텍스트 (null 허용)
     * @return 라인 단위 diff 목록 (양쪽 모두 비어 있으면 빈 목록)
     */
    public List<DiffLine> calculate(String fromText, String toText) {
        List<String> original = splitLines(fromText);
        List<String> revised = splitLines(toText);

        Patch<String> patch = DiffUtils.diff(original, revised);

        List<DiffLine> result = new ArrayList<>();
        int oi = 0;          // original 인덱스 (0-based)
        int oldLineNo = 1;   // from 라인 번호 (1-based)
        int newLineNo = 1;   // to 라인 번호 (1-based)

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int sourcePos = delta.getSource().getPosition();
            // delta 이전의 공통(EQUAL) 라인들을 먼저 적재
            while (oi < sourcePos) {
                result.add(new DiffLine(DiffType.EQUAL, oldLineNo++, newLineNo++, original.get(oi++)));
            }
            // delta 유형별 처리 — CHANGE는 DELETE + INSERT로 분해
            switch (delta.getType()) {
                case DELETE -> {
                    for (String line : delta.getSource().getLines()) {
                        result.add(new DiffLine(DiffType.DELETE, oldLineNo++, null, line));
                        oi++;
                    }
                }
                case INSERT -> {
                    for (String line : delta.getTarget().getLines()) {
                        result.add(new DiffLine(DiffType.INSERT, null, newLineNo++, line));
                    }
                }
                case CHANGE -> {
                    for (String line : delta.getSource().getLines()) {
                        result.add(new DiffLine(DiffType.DELETE, oldLineNo++, null, line));
                        oi++;
                    }
                    for (String line : delta.getTarget().getLines()) {
                        result.add(new DiffLine(DiffType.INSERT, null, newLineNo++, line));
                    }
                }
                case EQUAL -> {
                    for (String line : delta.getSource().getLines()) {
                        result.add(new DiffLine(DiffType.EQUAL, oldLineNo++, newLineNo++, line));
                        oi++;
                    }
                }
            }
        }
        // 남은 공통(EQUAL) 라인 적재
        while (oi < original.size()) {
            result.add(new DiffLine(DiffType.EQUAL, oldLineNo++, newLineNo++, original.get(oi++)));
        }
        return result;
    }

    /**
     * 텍스트를 라인 리스트로 분할한다. CRLF 정규화 후 개행 분할하며,
     * 후행 개행으로 인한 빈 라인은 제거하여 trailing newline 노이즈를 방지한다(REQ-REV-003).
     */
    private List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        // 기본 limit(0) split은 후행 빈 문자열을 제거 → trailing newline 노이즈 제거
        String[] arr = normalized.split("\n");
        return Arrays.asList(arr);
    }
}
