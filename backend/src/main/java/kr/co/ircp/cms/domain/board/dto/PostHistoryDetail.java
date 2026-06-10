package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 게시글 특정 버전 단건 본문 DTO (전체 본문 포함).
 *
 * <p>SPEC-CMS-POST-HISTORY-001 REQ-PH-004 — 단건 버전 조회는 title + content_html
 * 전체 본문 및 메타데이터를 반환한다.
 *
 * <p>REQ-PH-003 — {@code editorName}은 edited_by가 NULL이거나 사용자가 삭제된 경우
 * null일 수 있다(LEFT JOIN).
 *
 * @param id          이력 스냅샷 PK
 * @param version     버전 번호
 * @param editorName  수정자 표시명 (nullable)
 * @param editReason  수정 사유 (nullable)
 * @param editedAt    수정 일시
 * @param title       해당 버전의 제목
 * @param contentHtml 해당 버전의 본문 HTML
 */
public record PostHistoryDetail(
        Long id,
        int version,
        String editorName,
        String editReason,
        Instant editedAt,
        String title,
        String contentHtml
) {
}
