package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 설문 응답 헤더 엔티티.
 * REQ-BOARD-013: 응답자 정보, 응답 시작/제출 시각.
 *
 * <p>익명 설문(survey.is_anonymous=true)인 경우 respondent_id 는 NULL 로 강제 처리한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyResponse {

    private Long id;
    private Long surveyId;
    /** 익명 설문이거나 비로그인 응답 시 NULL. */
    private Long respondentId;
    /** SHA-256 hex (64자). */
    private String respondentIpHash;
    private Instant startedAt;
    private Instant submittedAt;
}
