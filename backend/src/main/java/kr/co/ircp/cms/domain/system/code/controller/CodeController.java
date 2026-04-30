package kr.co.ircp.cms.domain.system.code.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.system.code.dto.BulkCodesResponse;
import kr.co.ircp.cms.domain.system.code.dto.CodeRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeResponse;
import kr.co.ircp.cms.domain.system.code.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공통코드 API 컨트롤러.
 * REQ-SYSTEM-004-D
 */
@Tag(name = "System Code", description = "공통코드 관리 API")
@RestController
@RequestMapping("/api/v1/system/codes")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    @Operation(summary = "그룹별 코드 목록 (ACTIVE, sort_order ASC)")
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:CODE:READ')")
    public ResponseEntity<List<CodeResponse>> listByGroup(
            @RequestParam String groupCode) {
        return ResponseEntity.ok(codeService.listByGroup(groupCode));
    }

    @Operation(summary = "코드 벌크 조회 (여러 그룹 한번에)")
    @GetMapping("/bulk")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:READ')")
    public ResponseEntity<BulkCodesResponse> bulk(
            @RequestParam List<String> groups) {
        return ResponseEntity.ok(codeService.bulkByGroups(groups));
    }

    @Operation(summary = "코드 단건 조회")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:READ')")
    public ResponseEntity<CodeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(codeService.getById(id));
    }

    @Operation(summary = "코드 생성")
    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM:CODE:WRITE')")
    public ResponseEntity<CodeResponse> create(@Valid @RequestBody CodeRequest request) {
        return ResponseEntity.status(201).body(codeService.create(request));
    }

    @Operation(summary = "코드 수정")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:WRITE')")
    public ResponseEntity<CodeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CodeRequest request) {
        return ResponseEntity.ok(codeService.update(id, request));
    }

    @Operation(summary = "코드 삭제")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:WRITE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        codeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
