package kr.co.ircp.cms.common.dto;

/**
 * 라인 단위 diff 결과의 변경 유형.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003) — 두 revision 본문 비교 시
 * 각 라인을 동일/추가/삭제 세 가지로 분류한다. CHANGE(치환)는 별도 유형 없이
 * DELETE(이전 라인) + INSERT(이후 라인) 조합으로 표현한다.
 */
public enum DiffType {
    /** 양 version에 동일하게 존재하는 라인 */
    EQUAL,
    /** to(이후) version에만 새로 추가된 라인 */
    INSERT,
    /** from(이전) version에만 존재하다 삭제된 라인 */
    DELETE
}
