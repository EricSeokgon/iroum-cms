package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 페이지 이력 보존 정책 배치.
 *
 * <p>페이지당 최대 max-versions 개의 이력만 유지하고, 초과분(가장 오래된 버전)을
 * 매일 02:00 에 자동 삭제한다. REQ-PHIST-001.
 *
 * // @MX:NOTE: [AUTO] REQ-PHIST-001 — 페이지당 최대 max-versions 이력 유지. 초과 시 오래된 이력 삭제.
 * // @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageHistoryRetentionJob {

    private final PageHistoryMapper pageHistoryMapper;

    @Setter
    @Value("${page.history.retention.max-versions:50}")
    private int maxVersions;

    /** 보존 정책 적용. 삭제된 총 이력 건수를 반환한다. */
    public int run() {
        List<Long> pageIds = pageHistoryMapper.findPageIdsWithExcessHistory(maxVersions);
        int total = 0;
        for (Long pageId : pageIds) {
            int deleted = pageHistoryMapper.deleteOldestExceedingLimit(pageId, maxVersions);
            total += deleted;
            log.debug("pageId={} 이력 {}건 삭제", pageId, deleted);
        }
        log.info("PageHistoryRetentionJob 완료: {}개 페이지, 총 {}건 삭제", pageIds.size(), total);
        return total;
    }

    @Scheduled(cron = "${page.history.retention.cron:0 0 2 * * ?}", zone = "Asia/Seoul")
    public void scheduled() {
        run();
    }
}
