package kr.co.ircp.cms.domain.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.ircp.cms.domain.audit.dto.AuditLogResponse;
import kr.co.ircp.cms.domain.audit.dto.AuditLogSearchResponse;
import kr.co.ircp.cms.domain.audit.entity.AuditLog;
import kr.co.ircp.cms.domain.audit.repository.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
            @RequestParam(required = false) List<String> action,
            @RequestParam(value = "entity_type", required = false) String entityType,
            @RequestParam(required = false) List<String> severity,
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

    /** CSV 헤더 — 13개 컬럼, 컨트롤러/IT/문서 단일 출처. */
    private static final String CSV_HEADER =
            "id,event_time,actor_id,actor_role,action,entity_type,entity_id,"
                    + "severity,result,ip_address,trace_id,duration_ms,failure_reason";

    /**
     * 감사 로그 CSV 스트리밍 내보내기.
     *
     * <p>MyBatis ResultHandler + JDBC FORWARD_ONLY 커서로 대용량 결과를 행 단위로
     * 흘려보내며, StreamingResponseBody로 응답 본문을 청크 단위로 클라이언트에 전송한다.
     * 컨트롤러 클래스 레벨의 @PreAuthorize가 ADMIN/SUPER_ADMIN/SYSTEM:AUDIT 접근을 강제한다.
     *
     * <p>CSV 인코딩: RFC 4180 — 콤마/큰따옴표/개행 포함 필드는 큰따옴표로 감싸고
     * 내부 큰따옴표는 두 개로 이스케이프한다.
     */
    @Operation(summary = "감사 로그 CSV 내보내기 (커서 스트리밍)")
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) List<String> action,
            @RequestParam(value = "entity_type", required = false) String entityType,
            @RequestParam(required = false) List<String> severity,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime) {

        String filename = "audit-logs-"
                + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                + ".csv";

        StreamingResponseBody body = outputStream -> {
            // try-with-resources — 스트림 종료 시 flush + close 보장
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

                writer.write(CSV_HEADER);
                writer.write('\n');

                auditLogMapper.searchForExport(
                        action, entityType, severity, result, actorId, fromTime, toTime,
                        ctx -> {
                            AuditLog row = ctx.getResultObject();
                            try {
                                writer.write(toCsvRow(row));
                                writer.write('\n');
                            } catch (java.io.IOException ioe) {
                                // ResultHandler는 checked 예외를 던질 수 없으므로 unchecked로 래핑
                                throw new java.io.UncheckedIOException(ioe);
                            }
                        });

                writer.flush();
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /** AuditLog 한 행을 RFC 4180 CSV 라인으로 직렬화 (개행은 포함하지 않음). */
    private static String toCsvRow(AuditLog row) {
        StringBuilder sb = new StringBuilder(256);
        appendField(sb, row.getId() != null ? row.getId().toString() : "");
        sb.append(',');
        appendField(sb, row.getEventTime() != null ? row.getEventTime().toString() : "");
        sb.append(',');
        appendField(sb, row.getActorId() != null ? row.getActorId().toString() : "");
        sb.append(',');
        appendField(sb, row.getActorRole());
        sb.append(',');
        appendField(sb, row.getAction());
        sb.append(',');
        appendField(sb, row.getEntityType());
        sb.append(',');
        appendField(sb, row.getEntityId());
        sb.append(',');
        appendField(sb, row.getSeverity());
        sb.append(',');
        appendField(sb, row.getResult());
        sb.append(',');
        appendField(sb, row.getIpAddress());
        sb.append(',');
        appendField(sb, row.getTraceId());
        sb.append(',');
        appendField(sb, row.getDurationMs() != null ? row.getDurationMs().toString() : "");
        sb.append(',');
        appendField(sb, row.getFailureReason());
        return sb.toString();
    }

    /**
     * RFC 4180 필드 이스케이프.
     * 콤마/큰따옴표/CR/LF 중 하나라도 포함되면 큰따옴표로 감싸고 내부 ""는 ""로 이스케이프.
     * null은 빈 문자열로 표기.
     */
    private static void appendField(StringBuilder sb, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            sb.append(value);
            return;
        }
        sb.append('"');
        for (int i = 0, n = value.length(); i < n; i++) {
            char c = value.charAt(i);
            if (c == '"') {
                sb.append('"').append('"');
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
    }
}
