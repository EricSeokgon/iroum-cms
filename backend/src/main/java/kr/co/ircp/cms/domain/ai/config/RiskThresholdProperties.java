package kr.co.ircp.cms.domain.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 위험도 등급 임계값 설정 (application.yml {@code ai.risk.thresholds}).
 *
 * <p>SPEC-CMS-AI-001 — p&lt;green GREEN / p&lt;yellow YELLOW / p&lt;orange ORANGE / 그 외 RED.
 * 등급 경계는 운영 환경에서 외부 설정으로 조정 가능하다.
 */
@Component
@ConfigurationProperties(prefix = "ai.risk.thresholds")
public class RiskThresholdProperties {

    private double green = 0.25;
    private double yellow = 0.50;
    private double orange = 0.75;

    /**
     * defaultProbability를 등급으로 매핑한다 (단일 진실 소스).
     *
     * <p>p&lt;green → GREEN, p&lt;yellow → YELLOW, p&lt;orange → ORANGE, 그 외 RED.
     */
    // @MX:NOTE: [AUTO] 임계값은 ai.risk.thresholds 설정 외부화 — GREEN<0.25, YELLOW<0.50, ORANGE<0.75, RED>=0.75
    // @MX:SPEC: SPEC-CMS-AI-001
    public String resolveGrade(double defaultProbability) {
        if (defaultProbability < green) {
            return "GREEN";
        }
        if (defaultProbability < yellow) {
            return "YELLOW";
        }
        if (defaultProbability < orange) {
            return "ORANGE";
        }
        return "RED";
    }

    public double getGreen() {
        return green;
    }

    public void setGreen(double green) {
        this.green = green;
    }

    public double getYellow() {
        return yellow;
    }

    public void setYellow(double yellow) {
        this.yellow = yellow;
    }

    public double getOrange() {
        return orange;
    }

    public void setOrange(double orange) {
        this.orange = orange;
    }
}
