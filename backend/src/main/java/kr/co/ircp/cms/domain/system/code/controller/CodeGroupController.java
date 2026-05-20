package kr.co.ircp.cms.domain.system.code.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupResponse;
import kr.co.ircp.cms.domain.system.code.service.CodeGroupService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공통코드 그룹 API 컨트롤러.
 * REQ-SYSTEM-004-D
 * 경로: /api/v1/system/codes/groups (프론트엔드 system.ts 스펙)
 * PUT/DELETE는 groupCode 문자열로 식별
 */
@Tag(name = "System Code Group", description = "공통코드 그룹 관리 API")
@RestController
@RequestMapping("/api/v1/system/codes/groups")
@RequiredArgsConstructor
public class CodeGroupController {

    private final CodeGroupService codeGroupService;

    @Operation(summary = "코드 그룹 목록 조회")
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:CODE:READ')")
    public ResponseEntity<List<CodeGroupResponse>> list() {
        return ResponseEntity.ok(codeGroupService.listAll());
    }

    @Operation(summary = "코드 그룹 단건 조회 (id)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:READ')")
    public ResponseEntity<CodeGroupResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(codeGroupService.getById(id));
    }

    @Operation(summary = "코드 그룹 생성")
    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM:CODE:WRITE')")
    public ResponseEntity<CodeGroupResponse> create(@Valid @RequestBody CodeGroupRequest request) {
        return ResponseEntity.status(201).body(codeGroupService.create(request));
    }

    @Operation(summary = "코드 그룹 수정 (groupCode 문자열로 식별)")
    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:WRITE')")
    public ResponseEntity<CodeGroupResponse> update(
            @PathVariable String code,
            @Valid @RequestBody CodeGroupRequest request) {
        // groupCode로 id를 조회한 뒤 기존 update(id, ...) 호출
        Long id = codeGroupService.getByCode(code).id();
        return ResponseEntity.ok(codeGroupService.update(id, request));
    }

    @Operation(summary = "코드 그룹 삭제 (groupCode 문자열, RESTRICT)")
    @DeleteMapping("/{code}")
    @PreAuthorize("hasAuthority('SYSTEM:CODE:WRITE')")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        Long id = codeGroupService.getByCode(code).id();
        codeGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
