package kr.co.ircp.cms.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Q&A 답변 알림 환경설정 요청 DTO.
 *
 * <p>REQ-BOARD-014-D-4: INAPP 채널은 옵트아웃 불가, EMAIL만 허용.
 */
public record QnaNotificationPreferenceRequest(
        @NotNull @Valid QnaAnswerPreference qnaAnswer
) {
    /** Q&A 답변 알림 채널별 수신 동의 여부 (false = opt-out). */
    public record QnaAnswerPreference(
            boolean email
    ) {}
}
