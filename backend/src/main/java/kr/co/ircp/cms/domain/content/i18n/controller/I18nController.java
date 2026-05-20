package kr.co.ircp.cms.domain.content.i18n.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nResponse;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nUpsertRequest;
import kr.co.ircp.cms.domain.content.i18n.entity.I18nResource;
import kr.co.ircp.cms.domain.content.i18n.mapper.I18nResourceMapper;
import kr.co.ircp.cms.domain.content.i18n.service.I18nResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 다국어 리소스 REST 컨트롤러.
 * REQ-CONTENT-010-D: 다국어 리소스 조회 + bulk upsert
 */
@RestController
@RequestMapping("/api/v1/content/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final I18nResolver i18nResolver;
    private final I18nResourceMapper i18nResourceMapper;

    /**
     * 다국어 리소스 조회 (폴백 체인 포함).
     * REQ-CONTENT-010-D-2
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public ResponseEntity<I18nResponse> getI18nFields(
            @RequestParam String namespace,
            @RequestParam Long resourceId,
            @RequestParam(defaultValue = "ko") String lang) {
        return ResponseEntity.ok(i18nResolver.resolveFields(namespace, resourceId, lang));
    }

    /**
     * namespace 전체 목록 조회 (편집기용).
     * REQ-CONTENT-010-D-3: resourceId 없이 namespace만으로 목록 페이지 조회
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public ResponseEntity<Map<String, Object>> listByNamespace(
            @RequestParam String namespace,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<I18nResource> items = i18nResourceMapper.findByNamespace(namespace, page * size, size);
        int total = i18nResourceMapper.countByNamespace(namespace);
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page, "size", size));
    }

    /**
     * 다국어 리소스 배치 upsert.
     * REQ-CONTENT-010-D
     */
    @PutMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<Void> bulkUpsert(@Valid @RequestBody I18nUpsertRequest request) {
        i18nResolver.bulkUpsert(request.items());
        return ResponseEntity.ok().build();
    }
}
