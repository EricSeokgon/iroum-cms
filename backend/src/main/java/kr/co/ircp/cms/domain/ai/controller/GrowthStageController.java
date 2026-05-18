package kr.co.ircp.cms.domain.ai.controller;

import kr.co.ircp.cms.domain.ai.dto.GrowthStageQueryDto;
import kr.co.ircp.cms.domain.ai.dto.GrowthStageResultDto;
import kr.co.ircp.cms.domain.ai.service.GrowthStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 성장단계 예측 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-001 — 인증 사용자 대상. PII 없는 4개 비식별 파라미터만 받는다.
 * 캐시 키는 ksicCode+capitalAmount+foundingYear+revenueAmount (null 안전).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-AI-001 성장단계 예측 — @Cacheable(aiGrowthStage) TTL 1시간
// @MX:SPEC: SPEC-CMS-AI-001
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class GrowthStageController {

    private final GrowthStageService growthStageService;

    @GetMapping("/growth-stage")
    @Cacheable(cacheNames = "aiGrowthStage",
            key = "#ksicCode + '|' + #capitalAmount + '|' + #foundingYear + '|' "
                    + "+ (#revenueAmount == null ? 'NA' : #revenueAmount)")
    public ResponseEntity<GrowthStageResultDto> growthStage(
            @RequestParam(name = "ksicCode") String ksicCode,
            @RequestParam(name = "capitalAmount") Long capitalAmount,
            @RequestParam(name = "foundingYear") Integer foundingYear,
            @RequestParam(name = "revenueAmount", required = false) Long revenueAmount
    ) {
        GrowthStageResultDto result = growthStageService.predict(
                new GrowthStageQueryDto(ksicCode, capitalAmount, foundingYear, revenueAmount));
        return ResponseEntity.ok(result);
    }
}
