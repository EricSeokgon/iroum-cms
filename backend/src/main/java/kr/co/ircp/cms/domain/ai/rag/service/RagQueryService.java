package kr.co.ircp.cms.domain.ai.rag.service;

import kr.co.ircp.cms.domain.ai.rag.dto.RagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryResponse;
import org.springframework.security.core.Authentication;

/**
 * RAG 자연어 질의응답 오케스트레이션 서비스.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-001~014 — 임베딩 → pgvector 검색 → FTS 하이브리드
 * 재랭킹 → LLM 생성 → 폴백 → 캐시 → 비동기 로그 파이프라인.
 */
public interface RagQueryService {

    /**
     * RAG 질의 처리. ML 장애 시 FTS 단독 폴백(degraded=true, 200 — REQ-RAG-008~010).
     *
     * @param req            질문 요청 (1~1000자, 그 외 IllegalArgumentException → 400)
     * @param rawSessionRef  평문 세션 식별자(즉시 SHA-256 해시, 평문 미저장)
     * @param auth           인증 컨텍스트(회원이면 회원ID, 비회원 null)
     * @return 답변·출처·degraded·cached·queryRef
     */
    RagQueryResponse query(RagQueryRequest req, String rawSessionRef, Authentication auth);

    /**
     * 답변 만족도 피드백 멱등 갱신 (REQ-RAG-013, AC-RAG-004).
     *
     * @param req queryRef + HELPFUL/UNHELPFUL (그 외 값 IllegalArgumentException → 400)
     */
    void feedback(RagFeedbackRequest req);
}
