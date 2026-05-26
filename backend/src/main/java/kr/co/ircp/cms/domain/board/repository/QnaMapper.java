package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.Qna;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * Q&A MyBatis 매퍼.
 * REQ-BOARD-008: Q&A 질문/답변 워크플로
 */
@Mapper
public interface QnaMapper {

    /** Q&A 목록 조회 */
    List<Qna> findByQuestionerId(@Param("questionerId") Long questionerId);

    /** 전체 Q&A 목록 조회 (관리자용) */
    List<Qna> findAll();

    /** ID로 단건 조회 */
    Optional<Qna> findById(@Param("id") Long id);

    /** Q&A 삽입 */
    void insert(Qna qna);

    /** Q&A 수정 (답변 포함) */
    int update(Qna qna);

    /** Q&A 삭제 (소프트 삭제) */
    int deleteById(@Param("id") Long id);

    /**
     * 필터 기반 페이징 조회.
     * 비공개(isPrivate=true) 항목은 questioner 본인 또는 관리자만 조회 가능.
     * mine=true 이면 requesterId 기준 본인 작성 Q&A만 조회.
     */
    List<Qna> findWithFilters(
            @Param("status") String status,
            @Param("isPrivate") Boolean isPrivate,
            @Param("requesterId") Long requesterId,
            @Param("isAdmin") boolean isAdmin,
            @Param("keyword") String keyword,
            @Param("mine") boolean mine,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /** 필터 기반 카운트 */
    long countWithFilters(
            @Param("status") String status,
            @Param("isPrivate") Boolean isPrivate,
            @Param("requesterId") Long requesterId,
            @Param("isAdmin") boolean isAdmin,
            @Param("keyword") String keyword,
            @Param("mine") boolean mine
    );

    /** 답변 등록 (status를 ANSWERED로 변경, answered_at을 NOW()로 설정) */
    int updateAnswer(
            @Param("id") Long id,
            @Param("answerHtml") String answerHtml,
            @Param("answerText") String answerText,
            @Param("answererId") Long answererId
    );

    /** 상태만 변경 (CLOSED 등) */
    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status
    );
}
