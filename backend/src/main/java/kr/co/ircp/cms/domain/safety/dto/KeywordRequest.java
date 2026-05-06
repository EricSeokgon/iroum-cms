package kr.co.ircp.cms.domain.safety.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 키워드 신규 등록·수정 요청.
 * REQ-SAFETY-002-D
 */
public record KeywordRequest(
        @NotBlank String category,
        @NotBlank String code,
        @NotBlank String term,
        String description,
        List<String> synonyms
) {}
