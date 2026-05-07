package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.SurveyResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 설문 응답 헤더 MyBatis 매퍼.
 * REQ-BOARD-013-D-3: 응답 시작/제출 + 동일 사용자 중복 응답 감지
 */
@Mapper
public interface SurveyResponseMapper {

    /** 응답 헤더 INSERT (id 자동 채번 후 response 객체에 주입). */
    void insert(SurveyResponse response);

    /** 동일 사용자가 동일 설문에 이미 응답했는지 확인. */
    Optional<SurveyResponse> findByUserAndSurvey(
            @Param("surveyId") Long surveyId,
            @Param("respondentId") Long respondentId
    );

    /** 응답 제출 완료 처리 (submitted_at = NOW). */
    void markSubmitted(@Param("id") Long id);
}
