package kr.co.ircp.cms.domain.policy.program.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramCreateRequest;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramDetail;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSummary;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSyncResult;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramUpdateRequest;
import kr.co.ircp.cms.domain.policy.program.service.PolicyProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
 * 정책사업 마스터 REST 컨트롤러.
 * REQ-POLICY-001
 */
@RestController
@RequestMapping("/api/v1/policy")
@RequiredArgsConstructor
public class PolicyProgramController {

    private final PolicyProgramService programService;

    /** GET /api/v1/policy/programs */
    @GetMapping("/programs")
    public ResponseEntity<PageResponse<PolicyProgramSummary>> listPrograms(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(programService.listPrograms(status, industry, region, keyword, page, size));
    }

    /** GET /api/v1/policy/programs/{id} */
    @GetMapping("/programs/{id}")
    public ResponseEntity<PolicyProgramDetail> getProgram(@PathVariable Long id) {
        return ResponseEntity.ok(programService.getProgram(id));
    }

    /** POST /api/v1/policy/admin/programs */
    @PostMapping("/admin/programs")
    public ResponseEntity<PolicyProgramDetail> createProgram(@Valid @RequestBody PolicyProgramCreateRequest request) {
        PolicyProgramDetail created = programService.createProgram(request);
        return ResponseEntity.created(URI.create("/api/v1/policy/programs/" + created.id())).body(created);
    }

    /** PUT /api/v1/policy/admin/programs/{id} */
    @PutMapping("/admin/programs/{id}")
    public ResponseEntity<PolicyProgramDetail> updateProgram(
            @PathVariable Long id,
            @Valid @RequestBody PolicyProgramUpdateRequest request) {
        return ResponseEntity.ok(programService.updateProgram(id, request));
    }

    /** DELETE /api/v1/policy/admin/programs/{id} */
    @DeleteMapping("/admin/programs/{id}")
    public ResponseEntity<Void> deleteProgram(@PathVariable Long id) {
        programService.deleteProgram(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/policy/admin/programs/sync */
    @PostMapping("/admin/programs/sync")
    public ResponseEntity<PolicyProgramSyncResult> syncFromExternal(
            @RequestParam(defaultValue = "K_STARTUP") String sourceCode) {
        return ResponseEntity.ok(programService.syncFromExternal(sourceCode));
    }
}
