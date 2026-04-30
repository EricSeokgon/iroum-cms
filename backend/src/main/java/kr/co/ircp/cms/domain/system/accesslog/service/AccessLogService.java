package kr.co.ircp.cms.domain.system.accesslog.service;

import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogResponse;
import kr.co.ircp.cms.domain.system.accesslog.dto.AccessLogSearchRequest;
import kr.co.ircp.cms.domain.system.accesslog.entity.AccessLog;

import java.util.List;

/**
 * 접속 로그 서비스 인터페이스.
 * REQ-SYSTEM-001-D
 */
public interface AccessLogService {

    /** 접속 로그 비동기 저장 (filter에서 호출) */
    void record(AccessLog accessLog);

    /** 접속 로그 검색 (페이징) */
    List<AccessLogResponse> search(AccessLogSearchRequest req);

    /** 접속 로그 검색 총 건수 */
    long count(AccessLogSearchRequest req);
}
