package kr.co.ircp.cms.domain.email.template.admin.dto;

/**
 * 템플릿 렌더링 결과 (제목 + HTML 본문 + 평문 본문).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-010/030 — 렌더러·리졸버의 공통 반환 타입.
 *
 * @param subject  치환 완료된 제목
 * @param bodyHtml 치환 완료된 HTML 본문
 * @param bodyText 치환 완료된 평문 본문(없으면 null)
 */
public record RenderResult(String subject, String bodyHtml, String bodyText) {
}
