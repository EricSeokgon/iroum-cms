package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 첨부파일 MyBatis 매퍼.
 * REQ-BOARD-004: 첨부파일 업로드·목록
 * REQ-BOARD-005: 첨부파일 보안 다운로드
 */
@Mapper
public interface BbsAttachmentMapper {

    /** 게시글 첨부파일 목록 조회 */
    List<BbsAttachment> findByPostId(@Param("postId") Long postId);

    /** ID로 단건 조회 */
    Optional<BbsAttachment> findById(@Param("id") Long id);

    /** 첨부파일 수 조회 (게시글당) */
    int countByPostId(@Param("postId") Long postId);

    /** 첨부파일 삽입 */
    void insert(BbsAttachment attachment);

    /** 다운로드 횟수 1 증가 */
    int incrementDownloadCount(@Param("id") Long id);

    /** 첨부파일 삭제 (소프트 삭제: deleted_at = NOW()) */
    int deleteById(@Param("id") Long id);
}
