package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.SurveyCreateRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyDetail;
import kr.co.ircp.cms.domain.board.dto.SurveyResultDto;
import kr.co.ircp.cms.domain.board.dto.SurveySubmitRequest;
import kr.co.ircp.cms.domain.board.dto.SurveySummary;
import kr.co.ircp.cms.domain.board.dto.SurveyUpdateRequest;

/**
 * 설문조사 서비스 인터페이스.
 * REQ-BOARD-013: 설문 CRUD + 응답 제출 + 결과 통계.
 */
public interface SurveyService {

    /** 설문 페이징 조회 (status / keyword 필터). */
    PageResponse<SurveySummary> listSurveys(String status, String keyword, int page, int size);

    /** 설문 단건 조회 (질문 목록 포함). */
    SurveyDetail getSurvey(Long id);

    /** 설문 신규 생성 (관리자). */
    SurveyDetail createSurvey(SurveyCreateRequest req, Long createdBy);

    /** 설문 부분 수정 (관리자). 질문이 포함되면 기존 질문은 일괄 교체된다. */
    SurveyDetail updateSurvey(Long id, SurveyUpdateRequest req);

    /** 설문 소프트 삭제 (관리자). */
    void deleteSurvey(Long id);

    /** 설문 응답 제출 (인증/익명 모두 지원). */
    void submitResponse(Long surveyId, SurveySubmitRequest req, Long respondentId, String ipHash);

    /** 설문 결과 통계 (관리자). */
    SurveyResultDto getResults(Long surveyId);
}
