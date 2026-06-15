package kr.co.ircp.cms.domain.ai.tag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.ai.tag.mapper.AiTagRecommendationLogMapper;
import kr.co.ircp.cms.domain.ai.tag.model.AiTagRecommendationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 스마트 태그 추천/피드백 로그 비동기 적재 서비스.
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-011/012 — 추천 이벤트 1건당 1행, 피드백 이벤트 1건당 1행을
 * {@code aiLogExecutor}(AI-001 AsyncConfig) 스레드 풀에서 적재한다. 적재 실패가 사용자
 * 추천 응답을 차단·지연시키지 않는다(예외 흡수, REQ-AI-TAG-NFR-002).
 */
@Service
public class AiTagRecommendationLogService {

    private static final Logger log = LoggerFactory.getLogger(AiTagRecommendationLogService.class);

    private final AiTagRecommendationLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AiTagRecommendationLogService(AiTagRecommendationLogMapper mapper,
                                         ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 태그 추천 이벤트를 비동기로 로그에 적재한다 (event_type=SUGGESTED).
     */
    // @MX:NOTE: [AUTO] 비동기 적재 실패가 추천 응답을 차단하지 않음 — @Async("aiLogExecutor")
    // @MX:SPEC: SPEC-CMS-AI-004
    @Async("aiLogExecutor")
    public void logSuggested(String sessionRef, String contentType, String contentHash,
                             List<String> recommendedTags, Map<String, Double> scores,
                             String modelVersion) {
        try {
            mapper.insertSuggested(AiTagRecommendationLog.ofSuggested(
                    sessionRef, contentType, contentHash,
                    toJson(recommendedTags), toJson(scores), modelVersion));
        } catch (Exception e) {
            log.error("태그 추천 로그 적재 실패 (non-blocking): sessionRef={}, contentType={}",
                    sessionRef, contentType, e);
        }
    }

    /**
     * 태그 피드백(채택/거부) 이벤트를 비동기로 로그에 적재한다 (event_type=ACCEPTED/REJECTED).
     */
    // @MX:NOTE: [AUTO] 비동기 피드백 적재 — 향후 모델 파인튜닝 입력 보존 (REQ-AI-TAG-012)
    // @MX:SPEC: SPEC-CMS-AI-004
    @Async("aiLogExecutor")
    public void logFeedback(String sessionRef, String contentType, String contentHash,
                            String eventType, String tagValue) {
        try {
            mapper.insertFeedback(AiTagRecommendationLog.ofFeedback(
                    sessionRef, contentType, contentHash, eventType, tagValue));
        } catch (Exception e) {
            log.error("태그 피드백 로그 적재 실패 (non-blocking): sessionRef={}, eventType={}",
                    sessionRef, eventType, e);
        }
    }

    /** 객체를 JSON 문자열로 직렬화한다. 실패 시 null 반환(로그 비필수 데이터). */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("태그 로그 JSON 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
