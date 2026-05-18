package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.dto.SimulationResultDto;
import kr.co.ircp.cms.domain.ai.dto.SimulationStartDto;

import java.util.UUID;

/**
 * 익명 시뮬레이션 세션 서비스.
 *
 * <p>SPEC-CMS-AI-001 — 세션 생성/조회/PDF. 평문 IP 미저장(SHA-256 hash만),
 * ip-hash 기준 시간당 레이트리밋.
 */
public interface SimulationService {

    /**
     * 시뮬레이션 세션을 생성한다.
     *
     * @param dto    비식별 입력 (PII 없음)
     * @param ipHash SHA-256 hex (평문 IP 절대 전달 금지)
     */
    SimulationResultDto start(SimulationStartDto dto, String ipHash);

    /** 세션 결과 조회. 미존재/만료 시 AiSimulationNotFoundException. */
    SimulationResultDto getResult(UUID sessionId);

    /** PDF 보고서 생성 (pdf_status NONE → GENERATING → READY). */
    byte[] generatePdf(UUID sessionId);
}
