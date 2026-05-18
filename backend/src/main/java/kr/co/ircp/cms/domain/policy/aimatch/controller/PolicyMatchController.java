package kr.co.ircp.cms.domain.policy.aimatch.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyFeedbackRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.aimatch.service.PolicyFeedbackService;
import kr.co.ircp.cms.domain.policy.aimatch.service.PolicyMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하이브리드 정책 추천 공개 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-002 — 비회원 공개 API(SPEC-CMS-002 화이트리스트 등록).
 * 비회원 session_ref는 클라이언트 세션 토큰 헤더, 회원은 인증 컨텍스트 회원ID를 해시한다.
 * 평문 식별자는 서비스 인자로만 존재하며 즉시 SHA-256 해시된다(REQ-PM-014).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-AI-002 공개 추천/피드백 — session 식별자는 즉시 SHA-256 해시 (평문 미보관)
// @MX:SPEC: SPEC-CMS-AI-002
@RestController
@RequestMapping("/api/v1/ai/policy-match")
@RequiredArgsConstructor
public class PolicyMatchController {

    private final PolicyMatchService policyMatchService;
    private final PolicyFeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<PolicyMatchResponse> recommend(
            @RequestBody PolicyMatchRequest request,
            HttpServletRequest httpRequest) {
        // 평문 세션 식별자는 이 한 줄의 인자로만 존재 → 서비스에서 즉시 SHA-256 해시
        String rawSessionRef = resolveRawSessionRef(httpRequest);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(policyMatchService.recommend(rawSessionRef, request, auth));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@RequestBody PolicyFeedbackRequest request) {
        feedbackService.recordFeedback(request);
        return ResponseEntity.noContent().build();
    }

    /** 세션 식별자 원본: X-Session-Ref 헤더 우선, 없으면 remoteAddr (비회원 추적용). */
    private String resolveRawSessionRef(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader("X-Session-Ref");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return httpRequest.getRemoteAddr();
    }
}
