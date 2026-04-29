package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
import kr.co.ircp.cms.domain.board.dto.BbsMasterUpdateRequest;

import java.util.List;

/**
 * 게시판 마스터 서비스 인터페이스.
 * REQ-BOARD-001: 게시판 마스터 CRUD
 *
 * // @MX:ANCHOR: [AUTO] BbsMasterService — 게시판 마스터 비즈니스 계약
 * // @MX:REASON: PostService, CommentService, AttachmentService 등 3개 이상의 서비스가 게시판 설정을 참조
 * // @MX:SPEC: REQ-BOARD-001
 */
public interface BbsMasterService {

    /** 게시판 목록 조회 */
    List<BbsMasterSummary> listBoards();

    /** 게시판 단건 상세 조회 */
    BbsMasterDetail getBoard(Long id);

    /** 게시판 코드로 조회 */
    BbsMasterDetail getBoardByCode(String code);

    /** 게시판 생성 */
    BbsMasterDetail createBoard(BbsMasterCreateRequest request);

    /** 게시판 수정 */
    BbsMasterDetail updateBoard(Long id, BbsMasterUpdateRequest request);

    /** 게시판 삭제 (소프트 삭제) */
    void deleteBoard(Long id);
}
