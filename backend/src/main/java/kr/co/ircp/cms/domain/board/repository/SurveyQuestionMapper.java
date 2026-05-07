package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.SurveyQuestionRequest;
import kr.co.ircp.cms.domain.board.entity.SurveyQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 설문 질문 MyBatis 매퍼.
 * REQ-BOARD-013: 설문 단건 조회 시 질문 목록 fetch + 생성/수정 시 일괄 INSERT/DELETE
 */
@Mapper
public interface SurveyQuestionMapper {

    /** 특정 설문의 질문 목록을 sort_order 오름차순으로 조회. */
    List<SurveyQuestion> findBySurveyId(@Param("surveyId") Long surveyId);

    /** 질문 일괄 INSERT (PostgreSQL multi-row VALUES). */
    void insertBatch(
            @Param("surveyId") Long surveyId,
            @Param("questions") List<SurveyQuestionRequest> questions
    );

    /** 특정 설문의 모든 질문 삭제 (수정 시 사용). */
    void deleteBySurveyId(@Param("surveyId") Long surveyId);
}
