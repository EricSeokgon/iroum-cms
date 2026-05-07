package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 설문 응답 상세 엔티티 (질문별 답변 1행).
 * REQ-BOARD-013: 질문 유형별 답변 컬럼 분리 (TEXT/OPTIONS/RATING/DATE)
 */
@Data
@Builder
public class SurveyAnswer {

    private Long id;
    private Long responseId;
    private Long questionId;
    /** TEXT 유형 답변. */
    private String answerText;
    /** SINGLE/MULTI 유형 답변. JSONB → String (선택된 option value 배열). */
    private String answerOptions;
    /** RATING 유형 답변 (1~5). */
    private Short answerRating;
    /** DATE 유형 답변. */
    private LocalDate answerDate;
}
