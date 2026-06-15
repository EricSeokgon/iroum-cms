package kr.co.ircp.cms.domain.ai.tag.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagRecommendRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagRecommendResponse;
import kr.co.ircp.cms.domain.ai.tag.service.TagRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 스마트 태그 추천 공개 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-004 — 관리자 게시글 작성(인증) 및 시민 Q&A 작성(비인증, SecurityConfig
 * 화이트리스트) 양쪽에서 호출된다(REQ-AI-TAG-006/007). 평문 IP는 서비스 인자로만 존재하며
 * 즉시 SHA-256 해시된다(REQ-AI-TAG-013). ML 장애 시 503이 아닌 빈 배열 200으로 응답한다.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-AI-004 태그 추천/피드백 — 세션 식별자 즉시 SHA-256 해시 (평문 미보관)
// @MX:SPEC: SPEC-CMS-AI-004
@RestController
@RequestMapping("/api/v1/ai/tag-recommend")
@RequiredArgsConstructor
public class TagRecommendationController {

    private final TagRecommendationService service;

    @PostMapping
    public ResponseEntity<TagRecommendResponse> recommend(
            @RequestBody TagRecommendRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.recommendTags(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(
            @RequestBody TagFeedbackRequest request,
            HttpServletRequest httpRequest) {
        service.recordFeedback(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
