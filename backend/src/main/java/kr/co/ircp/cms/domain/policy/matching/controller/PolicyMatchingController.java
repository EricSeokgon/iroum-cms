package kr.co.ircp.cms.domain.policy.matching.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.policy.matching.dto.CompanyProfileUpsertRequest;
import kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.matching.service.PolicyMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정책 매칭 REST 컨트롤러.
 * REQ-POLICY-002
 */
@RestController
@RequestMapping("/api/v1/policy")
@RequiredArgsConstructor
public class PolicyMatchingController {

    private final PolicyMatchingService matchingService;

    /** POST /api/v1/policy/match — 매칭 실행. */
    @PostMapping("/match")
    public ResponseEntity<PolicyMatchResponse> match(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(matchingService.matchForCompany(companyId, topN));
    }

    /** GET /api/v1/policy/match/results — 캐시 조회. */
    @GetMapping("/match/results")
    public ResponseEntity<PolicyMatchResponse> getCachedResults(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "10") int topN) {
        return ResponseEntity.ok(matchingService.getCachedResults(companyId, topN));
    }

    /** PUT /api/v1/policy/company-profile — 기업 프로필 등록·수정. */
    @PutMapping("/company-profile")
    public ResponseEntity<Void> upsertProfile(@Valid @RequestBody CompanyProfileUpsertRequest request) {
        matchingService.upsertCompanyProfile(request);
        return ResponseEntity.noContent().build();
    }
}
