package kr.co.ircp.cms.common.dto;

import java.util.List;

/**
 * 단일 필드에 대한 두 revision 간 diff 응답.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003) — diff API는 비교 대상 필드별로
 * 하나씩(예: 게시물은 title/content, 페이지는 slug/title) 본 응답을 리스트로 반환한다.
 * 게시물·페이지 도메인이 공유하는 표현 계층 DTO (plan §1.1 — 공유는 diff 유틸·표현에 한함).
 *
 * @param field       비교 대상 필드명 (예: "title", "content", "slug")
 * @param fromVersion 비교 기준 이전 version
 * @param toVersion   비교 기준 이후 version
 * @param lines       라인 단위 diff 결과
 */
public record RevisionDiffResponse(
        String field,
        int fromVersion,
        int toVersion,
        List<DiffLine> lines
) {
}
