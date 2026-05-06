package kr.co.ircp.cms.domain.governance.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.dto.BatchExecutionLogResponse;
import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 배치 실행 이력 REST 컨트롤러.
 *
 * <p>SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010.
 */
@RestController
@RequestMapping("/api/v1/governance/batch-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BatchExecutionLogController {

    private final BatchExecutionLogService service;

    @GetMapping
    public ResponseEntity<PageResponse<BatchExecutionLogResponse>> list(
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<BatchExecutionLog> raw = service.findFiltered(jobGroup, status, from, to, page, size);
        PageResponse<BatchExecutionLogResponse> mapped = PageResponse.of(
                raw.content().stream().map(BatchExecutionLogResponse::from).toList(),
                raw.page(), raw.size(), raw.totalElements());
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchExecutionLogResponse> get(@PathVariable Long id) {
        return service.findById(id)
                .map(b -> ResponseEntity.ok(BatchExecutionLogResponse.from(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
