package kr.co.ircp.cms.domain.search.dto;

import java.util.List;

/**
 * 자동완성 응답 (REQ-SEARCH-005).
 */
public record AutocompleteResponse(List<AutocompleteItem> items) {}
