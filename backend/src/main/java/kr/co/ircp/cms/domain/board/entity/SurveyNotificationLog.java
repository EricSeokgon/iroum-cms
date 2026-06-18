package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 설문 알림 발송 로그 엔티티.
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-020: 멱등성 보장 (UNIQUE survey_id, type).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyNotificationLog {
    private Long id;
    private Long surveyId;
    /** SURVEY_OPENED | SURVEY_CLOSED | SURVEY_RESPONSE_LIMIT */
    private String type;
    /** SENT | FAILED */
    private String status;
    private String errorMessage;
    private Instant createdAt;
}
