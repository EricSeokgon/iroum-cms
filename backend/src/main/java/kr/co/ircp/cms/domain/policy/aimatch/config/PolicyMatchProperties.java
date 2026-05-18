package kr.co.ircp.cms.domain.policy.aimatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 하이브리드 정책 매칭 설정.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-003/008 — 캐시 TTL·하이브리드 가중치·Top-K 경계를
 * 외부화한다. {@code wRule + wSemantic = 1.0}, 음수 불가.
 * AI-001 {@code RiskThresholdProperties} 패턴 준용.
 */
@Component
@ConfigurationProperties(prefix = "ai.policy-match")
public class PolicyMatchProperties {

    /** 추천 결과 캐시 TTL(분) — 기본 30분. */
    private int cacheTtlMinutes = 30;

    /** 규칙 점수 가중치 — 기본 0.4. */
    private double wRule = 0.4;

    /** 시맨틱 점수 가중치 — 기본 0.6. */
    private double wSemantic = 0.6;

    /** Top-K 기본값 — 기본 10. */
    private int topKDefault = 10;

    /** Top-K 상한 — 기본 50. */
    private int topKMax = 50;

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public double getWRule() {
        return wRule;
    }

    public void setWRule(double wRule) {
        this.wRule = wRule;
    }

    public double getWSemantic() {
        return wSemantic;
    }

    public void setWSemantic(double wSemantic) {
        this.wSemantic = wSemantic;
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
}
