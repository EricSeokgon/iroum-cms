package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.entity.QnaNotificationLog;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationLogMapper;
import kr.co.ircp.cms.domain.board.repository.QnaNotificationOptoutMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Q&A 답변 알림 서비스 구현체.
 * REQ-BOARD-014-D: 멱등성·재시도·옵트아웃 정책 준수.
 *
 * // @MX:NOTE: [AUTO] INAPP 채널은 옵트아웃 불가(서비스 운영 정보 분류).
 *                       EMAIL은 qna_notification_optout 확인 후 발송.
 * // @MX:SPEC: REQ-BOARD-014-D
 * // @MX:ANCHOR: [AUTO] notifyAnswered — QnaServiceImpl.answerQna에서 호출되는 공개 알림 진입점.
 * // @MX:REASON: fan_in >= 2: QnaServiceImpl(직접) + QnaNotificationRetryJob(간접)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QnaNotificationServiceImpl implements QnaNotificationService {

    private final QnaNotificationLogMapper logMapper;
    private final QnaNotificationOptoutMapper optoutMapper;

    @Override
    @Transactional
    public void notifyAnswered(Long qnaId, Long questionerId, Long answererId) {
        // INAPP 채널: 항상 발송 (옵트아웃 불가)
        sendChannel(qnaId, questionerId, answererId, "INAPP");

        // EMAIL 채널: 옵트아웃 확인 후 발송
        if (!optoutMapper.existsByUserAndChannel(questionerId, "EMAIL")) {
            sendChannel(qnaId, questionerId, answererId, "EMAIL");
        }
    }

    @Override
    @Transactional
    public void retryFailed() {
        List<QnaNotificationLog> pending = logMapper.findPendingOrFailed();
        for (QnaNotificationLog item : pending) {
            attemptSend(item);
        }
    }

    @Override
    @Transactional
    public void updateEmailOptout(Long userId, boolean optout) {
        if (optout) {
            optoutMapper.upsert(userId, "EMAIL");
        } else {
            optoutMapper.delete(userId, "EMAIL");
        }
    }

    private void sendChannel(Long qnaId, Long recipientId, Long answererId, String channel) {
        // 멱등성 보장: 이미 PENDING 또는 SENT인 로그가 있으면 DB unique index가 차단
        QnaNotificationLog item = QnaNotificationLog.builder()
                .qnaId(qnaId)
                .answererId(answererId)
                .recipientId(recipientId)
                .channel(channel)
                .build();
        try {
            logMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // unique index 충돌 → 이미 발송됨 또는 발송 중
            log.warn("Q&A 알림 중복 발송 차단: qnaId={}, channel={}", qnaId, channel);
            return;
        }
        // 실제 발송 (stub — 실 구현에서 SMTP/이메일 서비스 연동)
        attemptSend(item);
    }

    private void attemptSend(QnaNotificationLog item) {
        try {
            // 실제 발송 로직 stub (INAPP: DB 알림 INSERT, EMAIL: SMTP enqueue)
            // TODO: 실 발송 구현 시 대체
            log.info("[AUTO] Q&A 알림 발송 (stub): id={}, channel={}, qnaId={}",
                    item.getId(), item.getChannel(), item.getQnaId());
            logMapper.markSent(item.getId());
        } catch (Exception e) {
            if (item.getRetryCount() >= 2) {
                // 3회째(0-indexed 2) 실패 → DEAD_LETTER
                logMapper.markDeadLetter(item.getId(), e.getMessage());
                log.error("Q&A 알림 DEAD_LETTER: id={}, error={}", item.getId(), e.getMessage());
            } else {
                logMapper.markFailed(item.getId(), e.getMessage());
                log.warn("Q&A 알림 발송 실패(재시도 예정): id={}, retryCount={}",
                        item.getId(), item.getRetryCount() + 1);
            }
        }
    }
}
