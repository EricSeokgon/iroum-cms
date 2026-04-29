package kr.co.ircp.cms.domain.board.repository;

import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 게시판 마스터 MyBatis 매퍼.
 * REQ-BOARD-001: 게시판 마스터 CRUD
 *
 * // @MX:ANCHOR: [AUTO] BbsMasterMapper — 게시판 CRUD 핵심 데이터 접근 계층
 * // @MX:REASON: BbsMasterService, PostService, CommentService, AttachmentService 등 4개 이상의 서비스에서 참조
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 XML 구현체 작성 필요
 */
@Mapper
public interface BbsMasterMapper {

    /** 게시판 목록 조회 (활성 상태만) */
    List<BbsMaster> findAll();

    /** ID로 단건 조회 */
    Optional<BbsMaster> findById(@Param("id") Long id);

    /** 코드로 단건 조회 */
    Optional<BbsMaster> findByCode(@Param("code") String code);

    /** 코드 중복 여부 확인 */
    boolean existsByCode(@Param("code") String code);

    /** 게시판 마스터 삽입 (id auto-set by DB SEQUENCE) */
    void insert(BbsMaster bbsMaster);

    /** 게시판 마스터 수정 */
    int update(BbsMaster bbsMaster);

    /** 게시판 마스터 삭제 (소프트 삭제: status=DELETED) */
    int deleteById(@Param("id") Long id);
}
