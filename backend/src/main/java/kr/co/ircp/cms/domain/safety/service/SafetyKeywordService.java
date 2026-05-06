package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.KeywordRequest;
import kr.co.ircp.cms.domain.safety.dto.KeywordSummary;

import java.util.List;

/**
 * 안전 키워드 사전 서비스.
 * REQ-SAFETY-002 (키워드 사전 관리)
 */
public interface SafetyKeywordService {

    List<KeywordSummary> listKeywords(String category);

    KeywordSummary createKeyword(KeywordRequest request);

    KeywordSummary updateKeyword(Long id, KeywordRequest request);

    void deactivateKeyword(Long id);
}
