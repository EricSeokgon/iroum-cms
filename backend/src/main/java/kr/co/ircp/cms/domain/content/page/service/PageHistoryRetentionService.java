package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페이지 이력 보존 정책(Retention) 서비스.
 *
 * <p>SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-001 — 페이지당 이력이 무한 증가하는 것을 방지한다.
 * 보존 한도({@code cms.history.max-versions}, 기본 50)를 초과하면 가장 오래된(version ASC)
 * 초과분을 정리한다.
 *
 * <ul>
 *   <li>정리는 항상 최신 version부터 N개를 보존하므로 가장 최근 항목(currentVersion)은 절대 삭제되지 않는다.</li>
 *   <li>정리 대상은 해당 페이지 단위로만 평가한다(전역 카운트가 아님).</li>
 *   <li>max-versions가 0 이하이면 정리하지 않고(무한 보존) 경고 로그만 남긴다.</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] PageHistoryRetentionService — REQ-PHIST-001 이력 보존 정책 정리
// @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001#REQ-PHIST-001
@Service
public class PageHistoryRetentionService {

    private static final Logger log = LoggerFactory.getLogger(PageHistoryRetentionService.class);

    private final PageHistoryMapper pageHistoryMapper;
    private final int maxVersions;

    public PageHistoryRetentionService(
            PageHistoryMapper pageHistoryMapper,
            @Value("${cms.history.max-versions:50}") int maxVersions) {
        this.pageHistoryMapper = pageHistoryMapper;
        this.maxVersions = maxVersions;
    }

    /**
     * 페이지 이력 보존 정책을 적용한다(보존 한도 초과 시 오래된 항목 정리).
     *
     * @param pageId         대상 페이지 ID
     * @param currentVersion 페이지의 현재 version (보존 보장 검증용 — 항상 최신이므로 정보성)
     */
    @Transactional
    public void enforceRetention(Long pageId, int currentVersion) {
        // REQ-PHIST-001 / AC-PHIST-003: max-versions <= 0 → 무한 보존, 정리 없이 경고 로그만
        if (maxVersions <= 0) {
            log.warn("페이지 이력 보존 한도가 설정되지 않았습니다(max-versions={}). pageId={} 정리를 건너뜁니다(무한 보존).",
                    maxVersions, pageId);
            return;
        }

        int count = pageHistoryMapper.countByPageId(pageId);
        if (count > maxVersions) {
            // AC-PHIST-001/002/004: 최신 maxVersions개만 보존, currentVersion은 최신이므로 항상 보존됨.
            pageHistoryMapper.deleteOldestByPageId(pageId, maxVersions);
            log.debug("페이지 이력 정리 완료. pageId={} 보존={} (이전 {}건)", pageId, maxVersions, count);
        }
    }
}
