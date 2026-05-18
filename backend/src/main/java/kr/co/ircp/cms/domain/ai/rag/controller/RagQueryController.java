package kr.co.ircp.cms.domain.ai.rag.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryResponse;
import kr.co.ircp.cms.domain.ai.rag.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 자연어 질의응답 공개 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-003 — 비회원 공개 API(SPEC-CMS-002 화이트리스트 등록).
 * 평문 세션 식별자는 서비스 인자로만 존재하며 즉시 SHA-256 해시된다(REQ-RAG-018).
 * ML 장애 시 503이 아닌 200 + degraded=true로 응답한다(REQ-RAG-010).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-AI-003 공개 RAG 질의/피드백 — session 식별자 즉시 SHA-256 해시 (평문 미보관)
// @MX:SPEC: SPEC-CMS-AI-003
@RestController
@RequestMapping("/api/v1/ai/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final RagQueryService ragQueryService;

    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(
            @RequestBody RagQueryRequest request,
            HttpServletRequest httpRequest) {
        // 평문 세션 식별자는 이 한 줄의 인자로만 존재 → 서비스에서 즉시 SHA-256 해시
        String rawSessionRef = resolveRawSessionRef(httpRequest);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(ragQueryService.query(request, rawSessionRef, auth));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@RequestBody RagFeedbackRequest request) {
        ragQueryService.feedback(request);
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
