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
}
