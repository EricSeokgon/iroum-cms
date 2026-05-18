package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.mapper.AiPredictionLogMapper;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * AI 예측 로그 비동기 적재 서비스.
 *
 * <p>SPEC-CMS-AI-001 — ML 추론 호출의 입력/출력/지연/상태를 별도 스레드 풀에서 적재한다.
 * 로그 적재 실패가 ML 응답 경로(예측 결과 반환)에 영향을 주지 않도록 분리한다.
 * 큐 포화 시 DiscardPolicy(AsyncConfig#aiLogExecutor)로 로그 유실을 허용한다.
 */
@Service
public class AiPredictionLogService {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionLogService.class);

    private final AiPredictionLogMapper aiPredictionLogMapper;

    public AiPredictionLogService(AiPredictionLogMapper aiPredictionLogMapper) {
        this.aiPredictionLogMapper = aiPredictionLogMapper;
    }

    /**
     * 예측 로그를 비동기로 적재한다 (aiLogExecutor 스레드 풀).
     *
     * <p>Spring @Async 규약상 반환형은 void. 적재 실패는 흡수하여 호출부에 전파하지 않는다.
     */
    // @MX:WARN: [AUTO] aiLogExecutor DiscardPolicy — 큐 포화(>500) 시 로그 유실. 예측 로그는 비필수 데이터
    // @MX:REASON: 비즈니스 응답 지연보다 로그 유실 허용이 설계 결정 (SPEC-CMS-AI-001 §4.3)
    // @MX:SPEC: SPEC-CMS-AI-001
    @Async("aiLogExecutor")
    public void logAsync(AiPredictionLog predictionLog) {
        try {
            aiPredictionLogMapper.insert(predictionLog);
        } catch (Exception e) {
            log.error("AI 예측 로그 적재 실패 (non-blocking): type={}, status={}",
                    predictionLog.getPredictionType(), predictionLog.getStatus(), e);
        }
    }
}
