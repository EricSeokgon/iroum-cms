package kr.co.ircp.cms.domain.governance.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.dto.DictionaryRequest;
import kr.co.ircp.cms.domain.governance.dto.DictionaryResponse;
import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.service.DataDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
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
import java.util.Map;

/**
 * 데이터 표준 사전 REST 컨트롤러.
 *
 * <p>SPEC-CMS-009 REQ-GOV-001~005.
 */
// @MX:ANCHOR: [AUTO] DictionaryController — 7개 엔드포인트 fan_in 진입점 (Step 2)
// @MX:REASON: 데이터 표준 사전 CRUD + Export + Freshness 비교의 단일 REST 진입점
// @MX:SPEC: SPEC-CMS-009#REQ-GOV-001
@RestController
@RequestMapping("/api/v1/governance/dictionary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DictionaryController {

    private final DataDictionaryService service;

    @GetMapping
    public ResponseEntity<PageResponse<DictionaryResponse>> list(
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<DataDictionary> raw = service.findFiltered(tableName, domain, status, page, size);
        PageResponse<DictionaryResponse> mapped = PageResponse.of(
                raw.content().stream().map(DictionaryResponse::from).toList(),
                raw.page(), raw.size(), raw.totalElements());
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DictionaryResponse> get(@PathVariable Long id) {
        return service.findById(id)
                .map(d -> ResponseEntity.ok(DictionaryResponse.from(d, service.findHistory(id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DictionaryResponse> create(@Valid @RequestBody DictionaryRequest req) {
        DataDictionary entity = DataDictionary.builder()
                .tableName(req.tableName())
                .columnName(req.columnName())
                .logicalNameKo(req.logicalNameKo())
                .logicalNameEn(req.logicalNameEn())
                .dataDomain(req.dataDomain())
                .dataType(req.dataType())
                .description(req.description())
                .isPii(req.isPii())
                .isRequired(req.isRequired())
                .status(req.status() == null ? "ACTIVE" : req.status())
                .build();
        DataDictionary created = service.create(entity);
        return ResponseEntity.created(URI.create("/api/v1/governance/dictionary/" + created.getId()))
                .body(DictionaryResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DictionaryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DictionaryRequest req,
            Authentication authentication) {
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jp) {
            userId = jp.userId();
        }
        DataDictionary updated = DataDictionary.builder()
                .id(id)
                .tableName(req.tableName())
                .columnName(req.columnName())
                .logicalNameKo(req.logicalNameKo())
                .logicalNameEn(req.logicalNameEn())
                .dataDomain(req.dataDomain())
                .dataType(req.dataType())
                .description(req.description())
                .isPii(req.isPii())
                .isRequired(req.isRequired())
                .status(req.status())
                .build();
        DataDictionary result = service.update(updated, userId);
        return ResponseEntity.ok(DictionaryResponse.from(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        return service.softDelete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format) {
        byte[] data = service.exportDictionary(format);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String fname = "data_dictionary." + (xlsx ? "xlsx" : "csv");
        String mime = xlsx
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv; charset=UTF-8";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fname + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }

    @GetMapping("/freshness")
    public ResponseEntity<Map<String, Object>> freshness() {
        return ResponseEntity.ok(service.compareWithSchema());
    }
}
