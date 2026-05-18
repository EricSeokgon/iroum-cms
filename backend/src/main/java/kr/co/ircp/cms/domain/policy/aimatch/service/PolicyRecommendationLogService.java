package kr.co.ircp.cms.domain.policy.aimatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.policy.aimatch.entity.PolicyRecommendationLogEntity;
import kr.co.ircp.cms.domain.policy.aimatch.repository.PolicyRecommendationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AI 정책 추천/피드백 로그 비동기 적재 서비스.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-004/012 — 추천(VIEWED)·피드백(CLICKED/APPLIED/DISMISSED)
 * 이벤트를 {@code aiLogExecutor}(AI-001 AsyncConfig) 스레드 풀에서 적재한다.
 * 적재 실패가 사용자 응답을 지연·실패시키지 않는다(DiscardPolicy).
 */
@Service
public class PolicyRecommendationLogService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRecommendationLogService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PolicyRecommendationLogMapper mapper;

    public PolicyRecommendationLogService(PolicyRecommendationLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 추천 이벤트(VIEWED) 비동기 적재. {@code sessionRef}는 이미 해시된 값이어야 한다.
     *
     * <p>Spring @Async 규약상 반환형 void. 적재 실패는 흡수한다.
     */
    // @MX:WARN: [AUTO] aiLogExecutor DiscardPolicy — 큐 포화(>500) 시 추천 로그 유실 허용
    // @MX:REASON: 추천 응답 지연보다 로그 유실 허용이 설계 결정 (SPEC-CMS-AI-002 REQ-PM-004)
    // @MX:SPEC: SPEC-CMS-AI-002
    @Async("aiLogExecutor")
    public void logRecommendation(String sessionRef,
                                  Map<String, Object> companyProfile,
                                  String queryText,
                                  List<Long> policyIds,
                                  Map<String, Object> mlScores) {
        try {
            PolicyRecommendationLogEntity entity = PolicyRecommendationLogEntity.builder()
                    .sessionRef(sessionRef)
                    .companyProfile(toJson(companyProfile))
                    .queryText(queryText)
                    .recommendedPolicyIds(toJson(policyIds))
                    .mlScores(toJson(mlScores))
                    .interactionType("VIEWED")
                    .policyId(null)
                    .interactedAt(null)
                    .build();
            mapper.insertLog(entity);
        } catch (Exception e) {
            log.error("AI 추천 로그 적재 실패 (non-blocking): sessionRef-len={}",
                    sessionRef == null ? 0 : sessionRef.length(), e);
        }
    }

    /**
     * 피드백 이벤트(CLICKED/APPLIED/DISMISSED) 비동기 적재.
     * 추천 행과 달리 company_profile은 빈 객체, policy_id·interacted_at을 채운다.
     */
    @Async("aiLogExecutor")
    public void logFeedback(String sessionRef, String interactionType, Long policyId) {
        try {
            PolicyRecommendationLogEntity entity = PolicyRecommendationLogEntity.builder()
                    .sessionRef(sessionRef)
                    .companyProfile("{}")
                    .queryText(null)
                    .recommendedPolicyIds(null)
                    .mlScores(null)
                    .interactionType(interactionType)
                    .policyId(policyId)
                    .interactedAt(Instant.now())
                    .build();
            mapper.insertLog(entity);
        } catch (Exception e) {
            log.error("AI 피드백 로그 적재 실패 (non-blocking): type={}", interactionType, e);
        }
    }

    private static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
