package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Q&A 답변 알림 발송 로그 엔티티.
 * REQ-BOARD-014-D: 멱등성·재시도(최대 3회)·DEAD_LETTER 추적
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QnaNotificationLog {
    private Long id;
    private Long qnaId;
    private Long answererId;
    private Long recipientId;
    private String channel; // INAPP/EMAIL/KAKAO/SMS
    private String status;  // PENDING/SENT/FAILED/DEAD_LETTER
    private short retryCount;
    private String lastError;
    private Instant sentAt;
    private Instant createdAt;
}
