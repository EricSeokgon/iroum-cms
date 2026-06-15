package kr.co.ircp.cms.domain.ai.tag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 스마트 태그 추천/피드백 로그 엔티티 (ai_tag_recommendation_log).
 *
 * <p>SPEC-CMS-AI-004 — 추천 이벤트 행(event_type=SUGGESTED, tag_value=NULL)과
 * 피드백 이벤트 행(event_type∈{ACCEPTED,REJECTED}, tag_value 채움)을 동일 테이블에
 * 적재한다. JSONB 컬럼은 String raw text로 다루고 XML에서 {@code ::jsonb} 캐스팅한다
 * (AI-002 패턴). {@code sessionRef}·{@code contentHash}는 SHA-256 해시(평문 미저장).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTagRecommendationLog {

    private Long id;
    private String sessionRef;
    private String contentType;
    private String contentHash;
    /** JSONB raw text — 순서 보존 추천 태그 배열. */
    private String recommendedTags;
    /** JSONB raw text — 태그별 신뢰도 점수 맵. */
    private String mlScores;
    private String modelVersion;
    private String eventType;
    private String tagValue;
    private Instant suggestedAt;
    private Instant interactedAt;

    /**
     * 추천 이벤트(SUGGESTED) 행을 생성한다. tag_value는 NULL이다.
     */
    public static AiTagRecommendationLog ofSuggested(String sessionRef, String contentType,
                                                     String contentHash, String recommendedTagsJson,
                                                     String mlScoresJson, String modelVersion) {
        return AiTagRecommendationLog.builder()
                .sessionRef(sessionRef)
                .contentType(contentType)
                .contentHash(contentHash)
                .recommendedTags(recommendedTagsJson)
                .mlScores(mlScoresJson)
                .modelVersion(modelVersion)
                .eventType("SUGGESTED")
                .build();
    }

    /**
     * 피드백 이벤트(ACCEPTED/REJECTED) 행을 생성한다. tag_value를 채운다.
     */
    public static AiTagRecommendationLog ofFeedback(String sessionRef, String contentType,
                                                    String contentHash, String eventType,
                                                    String tagValue) {
        return AiTagRecommendationLog.builder()
                .sessionRef(sessionRef)
                .contentType(contentType)
                .contentHash(contentHash)
                .eventType(eventType)
                .tagValue(tagValue)
                .interactedAt(Instant.now())
                .build();
    }
}
