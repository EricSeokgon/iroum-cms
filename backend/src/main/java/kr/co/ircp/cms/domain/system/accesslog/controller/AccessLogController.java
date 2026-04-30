package kr.co.ircp.cms.domain.system.accesslog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogResponse;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogSearchRequest;
import kr.co.ircp.cms.domain.system.accesslog.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 접속 로그 API 컨트롤러.
 * REQ-SYSTEM-001-D: GET /api/v1/system/access-logs (검색+페이징, ADMIN)
 */
@Tag(name = "System Access Log", description = "접속 로그 조회 API")
@RestController
@RequestMapping("/api/v1/system/access-logs")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogService accessLogService;

    @Operation(summary = "접속 로그 목록 조회 (페이징)")
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:LOG:READ')")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String pageUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AccessLogSearchRequest req = new AccessLogSearchRequest(from, to, statusCode, pageUrl, page, size);
        List<AccessLogResponse> items = accessLogService.search(req);
        long total = accessLogService.count(req);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", total);
        body.put("page", page);
        body.put("size", size);
        return ResponseEntity.ok(body);
    }
}
