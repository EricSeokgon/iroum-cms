package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 예약 발행 배치 잡.
 * SPEC-CMS-POST-SCHEDULE-001 REQ-POST-SCHEDULE-003: 1분 주기로 만기 예약 게시글 자동 발행.
 *
 * // @MX:NOTE: [AUTO] 매 1분 만기 예약 게시글(scheduled_at <= NOW)을 PUBLISHED 로 전환
 * // @MX:SPEC: REQ-POST-SCHEDULE-003
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostPublishJob {

    private final BbsPostMapper bbsPostMapper;

    /** 매 1분 만기 예약 게시글을 발행한다. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void publishDuePosts() {
        var duePosts = bbsPostMapper.findScheduledDue();
        if (duePosts.isEmpty()) {
            return;
        }
        log.info("예약 게시글 발행 시작: {}건", duePosts.size());
        // 멱등: publishScheduled 는 WHERE status='SCHEDULED' 조건부 UPDATE 로 중복 발행 방지
        duePosts.forEach(post -> bbsPostMapper.publishScheduled(post.getId()));
        log.info("예약 게시글 발행 완료: {}건", duePosts.size());
    }
}
