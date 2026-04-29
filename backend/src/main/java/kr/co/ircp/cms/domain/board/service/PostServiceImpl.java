package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 서비스 구현체.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색 + 조회수 dedupe
 *
 * // @MX:TODO: [AUTO] Step 2 GREEN — 실제 구현 필요. 현재 모든 메서드가 스텁 상태.
 * // @MX:SPEC: REQ-BOARD-002
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostServiceImpl implements PostService {

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsPostHistoryMapper bbsPostHistoryMapper;
    private final BbsViewLogMapper bbsViewLogMapper;

    @Override
    public PageResponse<PostSummary> listPosts(Long bbsMasterId, int page, int size) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    public PageResponse<PostSummary> searchPosts(Long bbsMasterId, String keyword, int page, int size) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public PostDetail getPost(Long id, Long userId, String ipHash) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public PostDetail createPost(PostCreateRequest request, Long authorId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public PostDetail updatePost(Long id, PostUpdateRequest request, Long editorId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public void deletePost(Long id, Long requesterId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }
}
