package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.dto.SurveyUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.Survey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 설문조사 마스터 MyBatis 매퍼.
 * REQ-BOARD-013: 설문 CRUD + 페이징·검색 + 응답수 카운트 증가
 */
@Mapper
public interface SurveyMapper {

    /** 필터 기반 설문 페이징 조회. */
    List<Survey> findWithFilters(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /** 필터 기반 카운트. */
    long countWithFilters(
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    /** ID로 단건 조회 (소프트 삭제 제외). */
    Optional<Survey> findById(@Param("id") Long id);

    /** 설문 신규 INSERT (id 자동 채번 후 survey 객체에 주입). */
    void insert(Survey survey);

    /** 설문 부분 UPDATE. */
    void update(@Param("id") Long id, @Param("req") SurveyUpdateRequest req);

    /** 소프트 삭제 (deleted_at=NOW, status=HIDDEN). */
    void softDelete(@Param("id") Long id);

    /** 응답 수 1 증가. */
    void incrementResponseCount(@Param("id") Long id);
}
