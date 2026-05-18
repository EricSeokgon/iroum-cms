package kr.co.ircp.cms.domain.ai.rag.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * pgvector cosine similarity 검색 매퍼.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-003 — {@code policy_program.embed_vector}에 대해
 * cosine distance(`<=>`) 오름차순 상위 N개 활성 정책을 조회한다. 읽기 전용.
 */
@Mapper
public interface PolicyEmbeddingRepository {

    /**
     * 질문 임베딩 벡터와의 cosine similarity 상위 limit개 활성 정책.
     *
     * @param vectorLiteral pgvector 리터럴 문자열 {@code "[0.1,0.2,...]"}
     * @param limit         반환 후보 수
     * @return 각 행: id, title, content, score(0~1 cosine similarity)
     */
    List<Map<String, Object>> searchByCosine(@Param("vectorLiteral") String vectorLiteral,
                                             @Param("limit") int limit);
}
