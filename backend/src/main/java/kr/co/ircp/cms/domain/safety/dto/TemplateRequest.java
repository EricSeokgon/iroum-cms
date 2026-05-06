package kr.co.ircp.cms.domain.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 가이드라인 템플릿 신규/수정 요청.
 * REQ-SAFETY-005-D-1, D-3
 */
public record TemplateRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotEmpty List<String> applicableIndustryCodes,
        @NotEmpty List<String> applicableGrades,
        @NotBlank String structure,
        String reviewStatus
) {}
