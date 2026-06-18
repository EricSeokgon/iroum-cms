package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.SurveyNotificationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 설문 알림 발송 로그 MyBatis 매퍼.
 * SPEC-CMS-SURVEY-001 REQ-SURVEY-014/020: 멱등성 — UNIQUE(survey_id, type) 충돌 시 DuplicateKeyException.
 */
@Mapper
public interface SurveyNotificationLogMapper {

    /** 로그 INSERT — UNIQUE 제약으로 멱등성 보장 (중복 시 DuplicateKeyException). */
    void insert(SurveyNotificationLog log);

    /** 설문별 발송 로그 조회. */
    List<SurveyNotificationLog> findBySurveyId(@Param("surveyId") Long surveyId);
}
