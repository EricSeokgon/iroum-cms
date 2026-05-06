package kr.co.ircp.cms.domain.dashboard.controller;

import kr.co.ircp.cms.domain.dashboard.dto.CacheInvalidateRequest;
import kr.co.ircp.cms.domain.dashboard.dto.CacheStatsResponse;
import kr.co.ircp.cms.domain.dashboard.service.CacheAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐시 관리 REST 컨트롤러.
 * REQ-VIZ-005-D-5
 */
@RestController
@RequestMapping("/api/v1/dashboard/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final CacheAdminService service;

    @PostMapping("/invalidate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<Void> invalidate(@RequestBody CacheInvalidateRequest req) {
        service.invalidate(req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<CacheStatsResponse> stats() {
        return ResponseEntity.ok(service.stats());
    }
}
