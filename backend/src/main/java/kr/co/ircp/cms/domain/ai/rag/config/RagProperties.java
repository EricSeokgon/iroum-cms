package kr.co.ircp.cms.domain.ai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 질의응답 설정.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-006/007/011 — 하이브리드 재랭킹 가중치,
 * LLM 컨텍스트 K 경계, 캐시 TTL을 외부화한다.
 * {@code wVector + wFts = 1.0} 권장, 음수 불가. AI-002 {@code PolicyMatchProperties} 패턴 준용.
 */
@Component
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    /** ragQueryCache TTL(분) — 기본 15분 (REQ-RAG-011). */
    private int cacheTtlMinutes = 15;

    /** ragQueryCache 최대 엔트리 — 기본 500. */
    private int cacheMaxSize = 500;

    /** pgvector cosine 점수 가중치 — 기본 0.6 (REQ-RAG-006). */
    private double wVector = 0.6;

    /** FTS tsvector 점수 가중치 — 기본 0.4 (REQ-RAG-006). */
    private double wFts = 0.4;

    /** LLM 컨텍스트 정책 수 기본값 — 기본 5 (REQ-RAG-007). */
    private int topKDefault = 5;

    /** LLM 컨텍스트 정책 수 상한 — 기본 10 (REQ-RAG-007). */
    private int topKMax = 10;

    /** 질문 최대 길이(자) — 초과 시 400 (AC-RAG-009). */
    private int maxQuestionLength = 1000;

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public double getWVector() {
        return wVector;
    }

    public void setWVector(double wVector) {
        this.wVector = wVector;
    }

    public double getWFts() {
        return wFts;
    }

    public void setWFts(double wFts) {
        this.wFts = wFts;
    }

    public int getTopKDefault() {
        return topKDefault;
    }

    public void setTopKDefault(int topKDefault) {
        this.topKDefault = topKDefault;
    }

    public int getTopKMax() {
        return topKMax;
    }

    public void setTopKMax(int topKMax) {
        this.topKMax = topKMax;
    }

    public int getMaxQuestionLength() {
        return maxQuestionLength;
    }

    public void setMaxQuestionLength(int maxQuestionLength) {
        this.maxQuestionLength = maxQuestionLength;
    }
}
