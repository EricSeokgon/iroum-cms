package kr.co.ircp.cms.domain.safety.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.safety.dto.KeywordRequest;
import kr.co.ircp.cms.domain.safety.dto.KeywordSummary;
import kr.co.ircp.cms.domain.safety.service.SafetyKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
import java.util.List;

/** 키워드 사전 REST 컨트롤러. REQ-SAFETY-002 (사전 관리) */
@RestController
@RequestMapping("/api/v1/safety/admin/keywords")
@RequiredArgsConstructor
public class SafetyKeywordController {

    private final SafetyKeywordService keywordService;

    @GetMapping
    public ResponseEntity<List<KeywordSummary>> list(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(keywordService.listKeywords(category));
    }

    @PostMapping
    public ResponseEntity<KeywordSummary> create(@Valid @RequestBody KeywordRequest request) {
        KeywordSummary created = keywordService.createKeyword(request);
        return ResponseEntity.created(URI.create("/api/v1/safety/admin/keywords/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<KeywordSummary> update(@PathVariable Long id,
                                                 @Valid @RequestBody KeywordRequest request) {
        return ResponseEntity.ok(keywordService.updateKeyword(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        keywordService.deactivateKeyword(id);
        return ResponseEntity.noContent().build();
    }
}
