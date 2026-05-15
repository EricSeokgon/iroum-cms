package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Q&A 답변 알림 옵트아웃 엔티티.
 * REQ-BOARD-014-D: 사용자별 채널 옵트아웃 (EMAIL/KAKAO/SMS만 허용; INAPP는 강제 발송)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QnaNotificationOptout {
    private Long userId;
    private String channel; // EMAIL/KAKAO/SMS
    private Instant optedOutAt;
}
