package kr.co.ircp.cms.domain.board.service;

/**
 * Q&A 답변 알림 서비스 인터페이스.
 * REQ-BOARD-014-D: 멱등성·재시도·옵트아웃 정책 준수.
 */
public interface QnaNotificationService {

    /** answerQna 완료 후 호출 — INAPP + EMAIL 채널 알림 발송. */
    void notifyAnswered(Long qnaId, Long questionerId, Long answererId);

    /** 재시도 배치 (스케줄러에서 호출). */
    void retryFailed();

    /** Q&A 답변 EMAIL 채널 옵트아웃 등록·해제. */
    void updateEmailOptout(Long userId, boolean optout);

    /** Q&A 답변 EMAIL 채널 수신 여부 조회 (true = 수신). */
    boolean isEmailEnabled(Long userId);
}
