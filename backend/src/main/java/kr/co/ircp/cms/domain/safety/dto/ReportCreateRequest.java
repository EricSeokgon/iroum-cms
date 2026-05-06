package kr.co.ircp.cms.domain.safety.dto;

/**
 * 가이드라인 보고서 생성 요청.
 * REQ-SAFETY-003-D-1
 */
public record ReportCreateRequest(
        /** null이면 본인 프로필 + risk_grade에 맞는 PUBLISHED 최신 템플릿 자동 선택. */
        Long templateId
) {}
