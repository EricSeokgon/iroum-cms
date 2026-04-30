package kr.co.ircp.cms.domain.system.accesslog.service;

import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogResponse;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogSearchRequest;
import kr.co.ircp.cms.domain.system.accesslog.entity.AccessLog;
import kr.co.ircp.cms.domain.system.accesslog.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 접속 로그 서비스 구현체.
 *
 * <p>REQ-SYSTEM-001-D — 비동기 로그 저장 + 검색/페이징.
 * 저장 실패는 ERROR 로깅으로 흡수하여 비즈니스 로직에 영향을 주지 않는다.
 */
// @MX:WARN: [AUTO] @Async("auditExecutor") — 저장 예외가 호출자에 전파되지 않음
// @MX:REASON: AccessLogFilter에서 응답 후 호출. 로그 유실보다 응답 영향을 방지하는 설계
@Service
@RequiredArgsConstructor
public class AccessLogServiceImpl implements AccessLogService {

    private static final Logger log = LoggerFactory.getLogger(AccessLogServiceImpl.class);

    private final AccessLogMapper accessLogMapper;

    @Override
    @Async("accessLogExecutor")
    public void record(AccessLog accessLog) {
        try {
            accessLogMapper.insert(accessLog);
        } catch (Exception e) {
            log.error("접속 로그 저장 실패 (non-blocking) pageUrl={} status={}",
                    accessLog.getPageUrl(), accessLog.getStatusCode(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessLogResponse> search(AccessLogSearchRequest req) {
        return accessLogMapper.findBySearch(req).stream()
                .map(AccessLogResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count(AccessLogSearchRequest req) {
        return accessLogMapper.countBySearch(req);
    }
}
