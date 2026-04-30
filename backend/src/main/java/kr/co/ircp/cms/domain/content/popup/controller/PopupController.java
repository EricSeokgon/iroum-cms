package kr.co.ircp.cms.domain.content.popup.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.popup.dto.PopupActiveResponse;
import kr.co.ircp.cms.domain.content.popup.dto.PopupRequest;
import kr.co.ircp.cms.domain.content.popup.dto.PopupResponse;
import kr.co.ircp.cms.domain.content.popup.service.PopupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
 * 팝업 REST 컨트롤러.
 * REQ-CONTENT-008-D: 팝업 CRUD + 활성 팝업 조회
 */
@RestController
@RequestMapping("/api/v1/content/popups")
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;

    /**
     * 활성 팝업 조회 (PUBLIC).
     * REQ-CONTENT-008-D-2, 008-D-3
     */
    @GetMapping("/active")
    public ResponseEntity<List<PopupActiveResponse>> getActivePopups(
            @RequestParam Long siteId) {
        List<PopupActiveResponse> result = popupService.getActivePopups(siteId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Popup-Limit", "5");
        return ResponseEntity.ok().headers(headers).body(result);
    }

    /**
     * 전체 팝업 목록 조회 (관리자용).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CONTENT:READ')")
    public List<PopupResponse> getPopupsBySite(@RequestParam Long siteId) {
        return popupService.getPopupsBySite(siteId);
    }

    /**
     * 팝업 등록.
     * REQ-CONTENT-008-D-1
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<PopupResponse> registerPopup(@Valid @RequestBody PopupRequest request) {
        return ResponseEntity.ok(popupService.registerPopup(request));
    }

    /**
     * 팝업 수정.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<PopupResponse> updatePopup(
            @PathVariable Long id,
            @Valid @RequestBody PopupRequest request) {
        return ResponseEntity.ok(popupService.updatePopup(id, request));
    }

    /**
     * 팝업 삭제.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTENT:WRITE')")
    public ResponseEntity<Void> deletePopup(@PathVariable Long id) {
        popupService.deletePopup(id);
        return ResponseEntity.noContent().build();
    }
}
