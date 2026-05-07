package kr.co.ircp.cms.domain.board.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import kr.co.ircp.cms.domain.board.dto.SurveyCreateRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyDetail;
import kr.co.ircp.cms.domain.board.dto.SurveyResultDto;
import kr.co.ircp.cms.domain.board.dto.SurveySubmitRequest;
import kr.co.ircp.cms.domain.board.dto.SurveySummary;
import kr.co.ircp.cms.domain.board.dto.SurveyUpdateRequest;
import kr.co.ircp.cms.domain.board.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 설문조사(Survey) REST 컨트롤러.
 * REQ-BOARD-013: 설문 CRUD + 응답 제출 + 결과 통계
 */
@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    /** GET /api/v1/surveys — 설문 목록 페이징 조회 (공개). */
    @GetMapping
    public ResponseEntity<PageResponse<SurveySummary>> listSurveys(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(surveyService.listSurveys(status, keyword, page, size));
    }

    /** GET /api/v1/surveys/{id} — 설문 단건 조회 (공개, 질문 포함). */
    @GetMapping("/{id}")
    public ResponseEntity<SurveyDetail> getSurvey(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getSurvey(id));
    }

    /** POST /api/v1/surveys — 설문 생성 (관리자). */
    @PostMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<SurveyDetail> createSurvey(
            @Valid @RequestBody SurveyCreateRequest request,
            Authentication authentication
    ) {
        Long createdBy = resolveUserId(authentication);
        SurveyDetail created = surveyService.createSurvey(request, createdBy);
        return ResponseEntity.created(URI.create("/api/v1/surveys/" + created.id())).body(created);
    }

    /** PUT /api/v1/surveys/{id} — 설문 수정 (관리자). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<SurveyDetail> updateSurvey(
            @PathVariable Long id,
            @Valid @RequestBody SurveyUpdateRequest request
    ) {
        return ResponseEntity.ok(surveyService.updateSurvey(id, request));
    }

    /** DELETE /api/v1/surveys/{id} — 설문 소프트 삭제 (관리자). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<Void> deleteSurvey(@PathVariable Long id) {
        surveyService.deleteSurvey(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/surveys/{id}/responses — 설문 응답 제출 (인증/익명 모두 허용).
     *
     * <p>익명 사용자는 IP 해시로만 식별되며, survey.is_anonymous=true 인 경우 로그인 사용자도
     * respondent_id 가 NULL 로 강제 처리된다.
     */
    @PostMapping("/{id}/responses")
    public ResponseEntity<Void> submitResponse(
            @PathVariable Long id,
            @Valid @RequestBody SurveySubmitRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        Long respondentId = resolveUserId(authentication);
        String remoteAddr = httpRequest.getRemoteAddr();
        String ipHash = HashUtil.sha256Hex(remoteAddr != null ? remoteAddr : "");
        surveyService.submitResponse(id, request, respondentId, ipHash);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/v1/surveys/{id}/results — 결과 통계 조회 (관리자 전용). */
    @GetMapping("/{id}/results")
    @PreAuthorize("hasAuthority('CONTENT:READ') or hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('CONTENT_ADMIN')")
    public ResponseEntity<SurveyResultDto> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(surveyService.getResults(id));
    }

    /**
     * Spring Security Authentication에서 사용자 ID를 추출.
     * principal이 Long이면 직접 사용하고, 그렇지 않으면 null(익명) 반환.
     */
    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof Number num) {
            return num.longValue();
        }
        return null;
    }
}
