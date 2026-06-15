package kr.co.ircp.cms.domain.ai.tag.mapper;

import kr.co.ircp.cms.domain.ai.tag.model.AiTagRecommendationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 스마트 태그 추천/피드백 로그 매퍼 (ai_tag_recommendation_log).
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-011/012 — 추천 행·피드백 행을 동일 테이블에 적재한다.
 */
// @MX:SPEC: SPEC-CMS-AI-004
@Mapper
public interface AiTagRecommendationLogMapper {

    /** 추천 이벤트(SUGGESTED) 행 적재. */
    void insertSuggested(AiTagRecommendationLog log);

    /** 피드백 이벤트(ACCEPTED/REJECTED) 행 적재. */
    void insertFeedback(AiTagRecommendationLog log);
}
