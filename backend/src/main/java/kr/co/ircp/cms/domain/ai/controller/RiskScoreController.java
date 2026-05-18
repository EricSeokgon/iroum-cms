package kr.co.ircp.cms.domain.ai.controller;

import kr.co.ircp.cms.domain.ai.dto.RiskScoreQueryDto;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreResultDto;
import kr.co.ircp.cms.domain.ai.service.RiskScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업 위험도 점수 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-001 — 인증 사용자 대상. PII 없는 4개 비식별 파라미터만 받는다.
 * riskGrade는 서버 임계 설정으로 재계산된 값이다.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-AI-001 위험도 점수 — 등급은 서버 임계로 산출
// @MX:SPEC: SPEC-CMS-AI-001
@RestController
@RequestMapping("/api/v1/ai/risk-score")
@RequiredArgsConstructor
public class RiskScoreController {

    private final RiskScoreService riskScoreService;

    @GetMapping
    public ResponseEntity<RiskScoreResultDto> riskScore(
            @RequestParam(name = "ksicCode") String ksicCode,
            @RequestParam(name = "capitalAmount") Long capitalAmount,
            @RequestParam(name = "foundingYear") Integer foundingYear,
            @RequestParam(name = "revenueAmount", required = false) Long revenueAmount
    ) {
        RiskScoreResultDto result = riskScoreService.score(
                new RiskScoreQueryDto(ksicCode, capitalAmount, foundingYear, revenueAmount));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/explain/{predictionId}")
    public ResponseEntity<RiskScoreResultDto> explain(
            @PathVariable("predictionId") Long predictionId) {
        return ResponseEntity.ok(riskScoreService.explain(predictionId));
    }
}
