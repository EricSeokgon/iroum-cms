package kr.co.ircp.cms.domain.ai.rag.dto;

import java.util.List;

/**
 * RAG 자연어 질의 응답.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-005 — 답변 본문, 출처 정책 목록, degraded 플래그.
 * {@code degraded=true}면 ML 장애로 FTS 단독 폴백된 간소 결과다(REQ-RAG-008/009).
 *
 * @param answer   생성형 답변(또는 폴백 안내 메시지)
 * @param sources  출처 정책 목록 (K 상한 이하, 0건 가능)
 * @param degraded ML 폴백 여부
 * @param cached   ragQueryCache 히트 여부
 * @param queryRef 피드백 상관 UUID
 */
public record RagQueryResponse(
        String answer,
        List<RagSource> sources,
        boolean degraded,
        boolean cached,
        String queryRef) {

    /** 캐시 히트 표기를 위한 복제(불변 record — cached만 교체). */
    public RagQueryResponse asCached() {
        return new RagQueryResponse(answer, sources, degraded, true, queryRef);
    }
}
