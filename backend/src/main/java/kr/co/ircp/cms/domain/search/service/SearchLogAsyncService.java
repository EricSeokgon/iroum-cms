package kr.co.ircp.cms.domain.search.service;

import kr.co.ircp.cms.domain.search.entity.SearchLog;
import kr.co.ircp.cms.domain.search.repository.SearchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 검색 로그 비동기 적재 서비스.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-008: 검색 응답 지연을 방지하기 위해
 * search_log INSERT를 searchLogExecutor 풀에서 비동기 처리한다.
 *
 * <p>큐 포화 시 DiscardPolicy로 유실되지만 비즈니스 영향은 없음(통계용).
 */
// @MX:NOTE: [AUTO] SPEC-CMS-010 검색 로그 비동기 적재 서비스 (REQ-SEARCH-008)
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogAsyncService {

    private final SearchLogMapper searchLogMapper;

    /**
     * 검색 로그 비동기 INSERT.
     * 실패 시 경고만 남기고 비즈니스 흐름에 영향을 주지 않는다.
     */
    @Async("searchLogExecutor")
    public void logSearch(SearchLog logEntry) {
        try {
            searchLogMapper.insert(logEntry);
        } catch (RuntimeException e) {
            log.warn("검색 로그 적재 실패 (무시): query={}, msg={}",
                    logEntry == null ? null : logEntry.getQuery(),
                    e.getMessage());
        }
    }

    /**
     * 클릭 추적 비동기 갱신.
     * 30분 윈도우/세션 매칭은 동기 호출자(SearchService)가 사전 검증한다.
     */
    @Async("searchLogExecutor")
    public void trackClick(Long searchLogId, String docType, Long docId, int rank) {
        try {
            searchLogMapper.updateClickInfo(searchLogId, docType, docId, rank);
        } catch (RuntimeException e) {
            log.warn("검색 클릭 추적 실패 (무시): searchLogId={}, msg={}",
                    searchLogId, e.getMessage());
        }
    }
}
