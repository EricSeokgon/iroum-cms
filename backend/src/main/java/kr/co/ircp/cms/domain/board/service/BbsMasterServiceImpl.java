package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
import kr.co.ircp.cms.domain.board.dto.BbsMasterUpdateRequest;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게시판 마스터 서비스 구현체.
 * REQ-BOARD-001: 게시판 마스터 CRUD
 *
 * // @MX:TODO: [AUTO] Step 2 GREEN — 실제 구현 필요. 현재 모든 메서드가 스텁 상태.
 * // @MX:SPEC: REQ-BOARD-001
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BbsMasterServiceImpl implements BbsMasterService {

    private final BbsMasterMapper bbsMasterMapper;

    @Override
    public List<BbsMasterSummary> listBoards() {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    public BbsMasterDetail getBoard(Long id) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    public BbsMasterDetail getBoardByCode(String code) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public BbsMasterDetail createBoard(BbsMasterCreateRequest request) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public BbsMasterDetail updateBoard(Long id, BbsMasterUpdateRequest request) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public void deleteBoard(Long id) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }
}
