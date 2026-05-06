package kr.co.ircp.cms.domain.safety.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.safety.dto.MatchRequest;
import kr.co.ircp.cms.domain.safety.dto.MatchResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileUpsertRequest;
import kr.co.ircp.cms.domain.safety.service.CompanySafetyProfileService;
import kr.co.ircp.cms.domain.safety.service.SafetyMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필 + 매칭 REST 컨트롤러.
 * REQ-SAFETY-002
 */
@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
public class SafetyProfileController {

    private final CompanySafetyProfileService profileService;
    private final SafetyMatchingService matchingService;

    /** POST /api/v1/safety/profiles — upsert. */
    @PostMapping("/profiles")
    public ResponseEntity<ProfileResponse> upsert(
            @Valid @RequestBody ProfileUpsertRequest request,
            @AuthenticationPrincipal Long companyId) {
        return ResponseEntity.ok(profileService.upsertProfile(companyId, request));
    }

    /** GET /api/v1/safety/profiles/me */
    @GetMapping("/profiles/me")
    public ResponseEntity<ProfileResponse> getMyProfile(
            @AuthenticationPrincipal Long companyId) {
        return ResponseEntity.ok(profileService.getMyProfile(companyId));
    }

    /** POST /api/v1/safety/match — 매칭 실행 (캐시 우선). */
    @PostMapping("/match")
    public ResponseEntity<MatchResponse> match(
            @RequestBody(required = false) MatchRequest request,
            @AuthenticationPrincipal Long companyId) {
        int topN = request == null ? 5 : request.topNOrDefault();
        return ResponseEntity.ok(matchingService.matchForCompany(companyId, topN));
    }

    /** GET /api/v1/safety/match/{profileId}/cached — 캐시 결과 조회. */
    @GetMapping("/match/{profileId}/cached")
    public ResponseEntity<MatchResponse> getCached(
            @PathVariable Long profileId,
            @RequestParam(defaultValue = "5") int topN) {
        return ResponseEntity.ok(matchingService.getCachedForProfile(profileId, topN));
    }
}
