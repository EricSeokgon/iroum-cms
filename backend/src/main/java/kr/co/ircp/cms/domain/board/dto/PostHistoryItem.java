package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 게시글 버전 히스토리 목록 항목 DTO (경량 — 본문 제외).
 *
 * <p>SPEC-CMS-POST-HISTORY-001 REQ-PH-002 — 목록은 메타데이터만 노출하고
 * content_html 전체 본문은 단건 조회({@link PostHistoryDetail})에서만 반환한다.
 *
 * <p>REQ-PH-003 — {@code editorName}은 edited_by가 NULL이거나 사용자가 삭제된 경우
 * null일 수 있다(LEFT JOIN). 호출 측은 null을 "알 수 없음"으로 표시한다.
 *
 * @param id         이력 스냅샷 PK
 * @param version    버전 번호
 * @param editorName 수정자 표시명 (nullable)
 * @param editReason 수정 사유 (nullable)
 * @param editedAt   수정 일시
 */
public record PostHistoryItem(
        Long id,
        int version,
        String editorName,
        String editReason,
        Instant editedAt
) {
}
