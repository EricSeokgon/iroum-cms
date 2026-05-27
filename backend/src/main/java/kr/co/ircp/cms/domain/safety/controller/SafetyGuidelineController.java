package kr.co.ircp.cms.domain.safety.controller;

import kr.co.ircp.cms.domain.safety.dto.GuidelineDetailResponse;
import kr.co.ircp.cms.domain.safety.dto.GuidelineSummaryResponse;
import kr.co.ircp.cms.domain.safety.entity.SafetyChecklistItem;
import kr.co.ircp.cms.domain.safety.entity.SafetyGuidelineTemplate;
import kr.co.ircp.cms.domain.safety.repository.SafetyChecklistItemMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyGuidelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공개 안전 가이드라인 조회 REST 컨트롤러.
 * REQ-PUBLIC-SAFETY-001 — 인증 불필요 (SecurityConfig permitAll)
 */
@RestController
@RequestMapping("/api/v1/safety/guidelines")
@RequiredArgsConstructor
public class SafetyGuidelineController {

    private final SafetyGuidelineTemplateMapper templateMapper;
    private final SafetyChecklistItemMapper checklistMapper;

    @GetMapping
    public ResponseEntity<List<GuidelineSummaryResponse>> list(
            @RequestParam(required = false) String industryCode) {
        List<GuidelineSummaryResponse> result = templateMapper.findPublished(industryCode)
                .stream()
                .map(GuidelineSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuidelineDetailResponse> get(@PathVariable Long id) {
        SafetyGuidelineTemplate template = templateMapper.findById(id)
                .filter(t -> "PUBLISHED".equals(t.getStatus()))
                .orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        List<SafetyChecklistItem> items = checklistMapper.findByTemplateId(id);
        return ResponseEntity.ok(GuidelineDetailResponse.from(template, items));
    }
}
