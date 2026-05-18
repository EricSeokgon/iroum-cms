package kr.co.ircp.cms.domain.ai.controller;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.ircp.cms.common.util.IpHashUtil;
import kr.co.ircp.cms.domain.ai.dto.SimulationResultDto;
import kr.co.ircp.cms.domain.ai.dto.SimulationStartDto;
import kr.co.ircp.cms.domain.ai.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 익명 시뮬레이션 세션 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-001 — 인증 사용자 대상. 평문 IP는 {@link IpHashUtil#sha256Hex}
 * 호출 인자로만 존재하며 즉시 해시되어 서비스로 전달된다(평문 미저장 불변식).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-AI-001 시뮬레이션 — IP는 즉시 SHA-256 해시 후 서비스 전달 (평문 미보관)
// @MX:SPEC: SPEC-CMS-AI-001
@RestController
@RequestMapping("/api/v1/ai/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/start")
    public ResponseEntity<SimulationResultDto> start(
            @RequestBody SimulationStartDto dto,
            HttpServletRequest httpRequest) {
        // 평문 IP는 이 한 줄의 인자로만 존재 → 즉시 SHA-256 해시
        String ipHash = IpHashUtil.sha256Hex(httpRequest.getRemoteAddr());
        SimulationResultDto result = simulationService.start(dto, ipHash);
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SimulationResultDto> getResult(
            @PathVariable("sessionId") UUID sessionId) {
        return ResponseEntity.ok(simulationService.getResult(sessionId));
    }

    @GetMapping("/{sessionId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable("sessionId") UUID sessionId) {
        byte[] pdf = simulationService.generatePdf(sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        "attachment; filename=\"simulation-" + sessionId + ".pdf\"")
                .body(pdf);
    }
}
