package kr.co.ircp.cms.domain.system.setting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingRequest;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingResponse;
import kr.co.ircp.cms.domain.system.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 시스템 설정 API 컨트롤러.
 * REQ-SYSTEM-005-D
 */
@Tag(name = "System Setting", description = "시스템 설정 관리 API")
@RestController
@RequestMapping("/api/v1/system/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService settingService;

    @Operation(summary = "전체 설정 목록 조회")
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:SETTING:READ')")
    public ResponseEntity<List<SystemSettingResponse>> list() {
        return ResponseEntity.ok(settingService.listAll());
    }

    @Operation(summary = "단일 설정 조회")
    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('SYSTEM:SETTING:READ')")
    public ResponseEntity<SystemSettingResponse> get(@PathVariable String key) {
        return ResponseEntity.ok(settingService.get(key));
    }

    @Operation(summary = "설정 값 저장/수정 (UPSERT)")
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('SYSTEM:SETTING:WRITE')")
    public ResponseEntity<SystemSettingResponse> put(
            @PathVariable String key,
            @Valid @RequestBody SystemSettingRequest request) {
        return ResponseEntity.ok(settingService.put(key, request));
    }
}
