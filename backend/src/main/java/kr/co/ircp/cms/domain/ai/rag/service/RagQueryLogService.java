package kr.co.ircp.cms.domain.ai.rag.service;

import kr.co.ircp.cms.domain.ai.rag.entity.AiRagQueryLog;
import kr.co.ircp.cms.domain.ai.rag.repository.RagQueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * RAG 질의 로그 비동기 적재 서비스.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-014 — 질의 1건당 1행을 {@code aiLogExecutor}
 * (AI-001 AsyncConfig) 스레드 풀에서 적재한다. 적재 실패가 사용자 응답을
 * 차단·지연시키지 않는다(예외 흡수).
 */
@Service
public class RagQueryLogService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryLogService.class);

    private final RagQueryLogRepository repository;

    public RagQueryLogService(RagQueryLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 질의 로그 비동기 적재. Spring @Async 규약상 반환형 void. 적재 실패는 흡수한다.
     */
    // @MX:NOTE: [AUTO] 비동기 적재 실패가 사용자 응답을 차단하지 않음 — @Async("aiLogExecutor")
    // @MX:SPEC: SPEC-CMS-AI-003
    @Async("aiLogExecutor")
    public void logQueryAsync(AiRagQueryLog entity) {
        try {
            repository.insertLog(entity);
        } catch (Exception e) {
            log.error("RAG 질의 로그 적재 실패 (non-blocking): queryRef={}",
                    entity == null ? null : entity.getQueryRef(), e);
        }
    }
}
