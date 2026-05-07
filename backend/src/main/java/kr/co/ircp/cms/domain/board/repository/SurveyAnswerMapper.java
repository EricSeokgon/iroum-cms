package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.SurveyAnswerRequest;
import kr.co.ircp.cms.domain.board.entity.SurveyAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 설문 답변 상세 MyBatis 매퍼.
 * REQ-BOARD-013-D-3, D-5: 답변 일괄 INSERT + 결과 통계 집계.
 */
@Mapper
public interface SurveyAnswerMapper {

    /** 답변 일괄 INSERT (PostgreSQL multi-row VALUES). */
    void insertBatch(
            @Param("responseId") Long responseId,
            @Param("answers") List<SurveyAnswerRequest> answers
    );

    /** 특정 응답 헤더의 답변 전체 조회. */
    List<SurveyAnswer> findByResponseId(@Param("responseId") Long responseId);

    /**
     * 결과 통계용 원시 집계.
     *
     * <p>설문에 속한 모든 질문 × 응답된 답변을 LEFT JOIN 하여 반환한다.
     * 각 row 는 question_id, question_text, question_type, total_responses, answer_count,
     * answer_text, answer_options, answer_rating, answer_date 컬럼을 가진다.
     * 서비스 계층에서 question_type 별 분기로 SurveyResultDto 를 가공한다.
     */
    List<Map<String, Object>> aggregateByQuestion(@Param("surveyId") Long surveyId);
}
