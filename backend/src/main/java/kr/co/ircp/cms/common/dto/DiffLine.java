package kr.co.ircp.cms.common.dto;

/**
 * Diff 결과의 단일 라인 표현.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003) — 게시물/페이지 revision 비교의
 * 라인 단위 결과 요소. 프론트 DiffViewer가 색상 구분 렌더링에 사용한다.
 *
 * <p>라인 번호 규칙:
 * <ul>
 *   <li>EQUAL: oldLineNo, newLineNo 모두 존재</li>
 *   <li>INSERT: oldLineNo = null (이전 version에 없음), newLineNo 존재</li>
 *   <li>DELETE: oldLineNo 존재, newLineNo = null (이후 version에 없음)</li>
 * </ul>
 *
 * @param type      변경 유형 (EQUAL/INSERT/DELETE)
 * @param oldLineNo 이전(from) version 기준 1-based 라인 번호 (INSERT면 null)
 * @param newLineNo 이후(to) version 기준 1-based 라인 번호 (DELETE면 null)
 * @param text      해당 라인 텍스트
 */
public record DiffLine(
        DiffType type,
        Integer oldLineNo,
        Integer newLineNo,
        String text
) {
}
