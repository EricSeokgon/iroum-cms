package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자 인앱(INAPP) 알림 수신함 엔티티.
 * REQ-BOARD-014-D-2: INAPP 채널 알림 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationInbox {
    private Long id;
    private Long userId;
    /** 알림 유형 — 예: QNA_ANSWERED */
    private String type;
    private String title;
    private String body;
    /** 참조 엔티티 PK (예: qna_id) */
    private Long refId;
    /** 참조 엔티티 타입 (예: QNA) */
    private String refType;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
