package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 설문 질문 엔티티.
 * REQ-BOARD-013: 질문 텍스트, 유형(SINGLE/MULTI/TEXT/RATING/DATE), 필수 여부, 정렬 순서, 보기 옵션
 */
@Data
@Builder
public class SurveyQuestion {

    private Long id;
    private Long surveyId;
    private String questionText;
    /** SINGLE / MULTI / TEXT / RATING / DATE */
    private String questionType;
    private boolean required;
    private int sortOrder;
    /** JSONB → String. SINGLE/MULTI 일 때만 NOT NULL. */
    private String options;
}
