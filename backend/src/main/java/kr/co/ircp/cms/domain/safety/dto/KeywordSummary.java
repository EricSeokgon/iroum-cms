package kr.co.ircp.cms.domain.safety.dto;

import java.util.List;

/** 키워드 응답 (동의어 포함). */
public record KeywordSummary(
        Long id,
        String category,
        String code,
        String term,
        String description,
        String status,
        List<String> synonyms
) {}
