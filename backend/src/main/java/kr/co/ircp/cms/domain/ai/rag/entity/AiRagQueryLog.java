package kr.co.ircp.cms.domain.ai.rag.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * RAG 질의/피드백 로그 엔티티 (ai_rag_query_log).
 *
 * <p>SPEC-CMS-AI-003 — 질의 1건당 1행 적재(REQ-RAG-014, 비동기). 피드백은
 * {@code queryRef}로 동일 행을 멱등 갱신한다(REQ-RAG-013). JSONB 컬럼은 String
 * raw text로 다루고 XML에서 {@code ::jsonb} 캐스팅한다(AI-002 패턴).
 * {@code questionHash}·{@code sessionRef}는 SHA-256 해시(평문 미저장).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRagQueryLog {

    private Long id;
    private String queryRef;
    private String questionHash;
    private String sessionRef;
    /** JSONB raw text — 검색된 정책 ID 배열. */
    private String retrievedPolicyIds;
    private Integer answerQualityScore;
    private String feedback;
    private Integer latencyMs;
    private boolean cacheHit;
    private boolean degraded;
    private Instant queriedAt;
    private Instant feedbackAt;
}
