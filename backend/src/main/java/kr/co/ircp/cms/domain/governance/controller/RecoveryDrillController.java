package kr.co.ircp.cms.domain.governance.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.governance.dto.RecoveryDrillRequest;
import kr.co.ircp.cms.domain.governance.dto.RecoveryDrillResponse;
import kr.co.ircp.cms.domain.governance.entity.RecoveryDrillLog;
import kr.co.ircp.cms.domain.governance.service.RecoveryDrillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 복구 시험 이력 REST 컨트롤러.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011~012.
 */
@RestController
@RequestMapping("/api/v1/governance/recovery-drills")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RecoveryDrillController {

    private final RecoveryDrillService service;

    @GetMapping
    public ResponseEntity<List<RecoveryDrillResponse>> list(
            @RequestParam(required = false) String drillType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Integer year) {
        List<RecoveryDrillResponse> ret = service.findFiltered(drillType, result, year).stream()
                .map(RecoveryDrillResponse::from).toList();
        return ResponseEntity.ok(ret);
    }

    @PostMapping
    public ResponseEntity<RecoveryDrillResponse> create(@Valid @RequestBody RecoveryDrillRequest req) {
        RecoveryDrillLog entity = RecoveryDrillLog.builder()
                .drillDate(req.drillDate())
                .drillType(req.drillType())
                .result(req.result())
                .rtoActualMin(req.rtoActualMin())
                .rpoActualMin(req.rpoActualMin())
                .rtoTargetMin(req.rtoTargetMin())
                .rpoTargetMin(req.rpoTargetMin())
                .performedBy(req.performedBy())
                .checklistJson(req.checklistJson())
                .notes(req.notes())
                .build();
        RecoveryDrillLog created = service.create(entity);
        return ResponseEntity.created(URI.create("/api/v1/governance/recovery-drills/" + created.getId()))
                .body(RecoveryDrillResponse.from(created));
    }
}
