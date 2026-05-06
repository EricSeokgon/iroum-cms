package kr.co.ircp.cms.domain.safety.dto;

/**
 * 템플릿 미리보기 요청.
 * REQ-SAFETY-005-D-4
 */
public record PreviewRequest(
        String riskGrade,
        String industryCode,
        String sampleProfileName
) {}
