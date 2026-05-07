package kr.co.ircp.cms.domain.board.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Q&A 알림 재시도 배치.
 * REQ-BOARD-014-D-3: 지수 백오프(1분, 2분, 4분) 최대 3회.
 *
 * // @MX:NOTE: [AUTO] 지수 백오프는 DB retry_count + cron 주기 조합으로 구현.
 *                       실 운영에서는 1분 단위 cron + retry_count 기반 지연 시간 필터링 권장.
 * // @MX:SPEC: REQ-BOARD-014-D-3
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QnaNotificationRetryJob {

    private final QnaNotificationService qnaNotificationService;

    /** 1분마다 실행 (지수 백오프는 서비스 레이어에서 retry_count로 판단). */
    @Scheduled(cron = "0 * * * * *")
    public void retryFailedNotifications() {
        log.debug("Q&A 알림 재시도 배치 실행");
        qnaNotificationService.retryFailed();
    }
}
