package kr.co.ircp.cms.common.service;

import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.system.setting.mapper.SystemSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘텐츠 리비전 이력 보존 정책 구현체.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-006) — {@code content.revision.maxPerEntity}
 * (기본 50)를 초과한 오래된 이력을 정리한다. 저장 경로의 finalizer 성격이므로 best-effort —
 * 어떤 예외도 호출자에게 전파하지 않고 WARN 로깅 후 반환한다.
 */
@Service
public class RevisionRetentionServiceImpl implements RevisionRetentionService {

    private static final Logger log = LoggerFactory.getLogger(RevisionRetentionServiceImpl.class);

    /** system_setting 키. */
    private static final String MAX_PER_ENTITY_KEY = "content.revision.maxPerEntity";
    /** 설정 미존재/파싱 실패 시 기본 보존 개수. */
    private static final int DEFAULT_MAX_PER_ENTITY = 50;

    private final SystemSettingMapper systemSettingMapper;
    private final BbsPostHistoryMapper bbsPostHistoryMapper;
    private final PageHistoryMapper pageHistoryMapper;

    public RevisionRetentionServiceImpl(SystemSettingMapper systemSettingMapper,
                                        BbsPostHistoryMapper bbsPostHistoryMapper,
                                        PageHistoryMapper pageHistoryMapper) {
        this.systemSettingMapper = systemSettingMapper;
        this.bbsPostHistoryMapper = bbsPostHistoryMapper;
        this.pageHistoryMapper = pageHistoryMapper;
    }

    @Override
    @Transactional
    public void prunePostHistory(Long postId) {
        try {
            int max = getMaxPerEntity();
            long count = bbsPostHistoryMapper.countByPostId(postId);
            if (count > max) {
                // 최신 max개만 남기고 오래된 이력 삭제 (초과분 count-max건이 제거됨)
                bbsPostHistoryMapper.deleteOldestByPostId(postId, max);
            }
        } catch (Exception e) {
            // best-effort: 보존 정리 실패는 저장 트랜잭션 결과에 영향을 주지 않는다.
            log.warn("게시물 이력 보존 정리 실패 (best-effort, 무시). postId={}", postId, e);
        }
    }

    @Override
    @Transactional
    public void prunePageHistory(Long pageId) {
        try {
            int max = getMaxPerEntity();
            long count = pageHistoryMapper.countByPageId(pageId);
            if (count > max) {
                pageHistoryMapper.deleteOldestByPageId(pageId, max);
            }
        } catch (Exception e) {
            log.warn("페이지 이력 보존 정리 실패 (best-effort, 무시). pageId={}", pageId, e);
        }
    }

    /** content.revision.maxPerEntity 설정 조회. 미존재/파싱 실패 시 기본값 50. */
    private int getMaxPerEntity() {
        return systemSettingMapper.findByKey(MAX_PER_ENTITY_KEY)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getValue().trim());
                    } catch (NumberFormatException ex) {
                        log.warn("content.revision.maxPerEntity 파싱 실패 '{}', 기본값 {} 적용",
                                s.getValue(), DEFAULT_MAX_PER_ENTITY);
                        return DEFAULT_MAX_PER_ENTITY;
                    }
                })
                .orElse(DEFAULT_MAX_PER_ENTITY);
    }
}
