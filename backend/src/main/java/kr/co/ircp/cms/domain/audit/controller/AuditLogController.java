package kr.co.ircp.cms.domain.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.ircp.cms.domain.audit.dto.AuditLogResponse;
import kr.co.ircp.cms.domain.audit.dto.AuditLogSearchResponse;
import kr.co.ircp.cms.domain.audit.entity.AuditLog;
import kr.co.ircp.cms.domain.audit.repository.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 감사 로그 조회 API 컨트롤러.
 *
 * <p>SPEC-CMS-005 §7 — ROLE_ADMIN 전용 감사 로그 조회 엔드포인트.
 * 적재(write)는 AuditLogAspect가 담당하며, 이 컨트롤러는 읽기 전용이다.
 */
@Tag(name = "System Audit", description = "감사 로그 조회 API (관리자 전용)")
@RestController
@RequestMapping("/api/v1/system/audit-logs")
@RequiredArgsConstructor
// @MX:ANCHOR: [AUTO] AuditLogController — 감사 로그 읽기 전용 API 진입점
// @MX:REASON: SPEC-CMS-005 §7 보안 요건: ROLE_ADMIN 한정 접근
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('SYSTEM:AUDIT')")
public class AuditLogController {

    private final AuditLogMapper auditLogMapper;

    @Operation(summary = "감사 로그 목록 조회 (동적 필터 + 페이징)")
    @GetMapping
    public ResponseEntity<AuditLogSearchResponse> search(
            @RequestParam(required = false) String action,
            @RequestParam(value = "entity_type", required = false) String entityType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {

        int offset = Math.max(0, page - 1) * size;
        List<AuditLog> items = auditLogMapper.search(
                action, entityType, severity, result, actorId, fromTime, toTime, offset, size);
        long total = auditLogMapper.countSearch(
                action, entityType, severity, result, actorId, fromTime, toTime);

        List<AuditLogResponse> responses = items.stream()
                .map(AuditLogResponse::from)
                .toList();

        return ResponseEntity.ok(new AuditLogSearchResponse(responses, total, page, size));
    }

    @Operation(summary = "감사 로그 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> findById(@PathVariable Long id) {
        return auditLogMapper.findById(id)
                .map(AuditLogResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "CRITICAL 심각도 최신 로그 조회")
    @GetMapping("/critical")
    public ResponseEntity<List<AuditLogResponse>> findCritical(
            @RequestParam(defaultValue = "50") int limit) {
        List<AuditLogResponse> responses = auditLogMapper.findCritical(limit).stream()
                .map(AuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // @MX:TODO: CSV export — 대용량 결과 스트리밍 구현 필요 (현재 placeholder)
    @Operation(summary = "감사 로그 CSV 내보내기 (미구현)")
    @GetMapping("/export")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok("id,event_time,actor_id,action,entity_type,severity,result");
    }
}
