package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostAdminSummary;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게시글 관리자 모더레이션 서비스 구현체.
 * SPEC-CMS-POST-MODERATE-001
 */
@Service
@RequiredArgsConstructor
public class PostAdminServiceImpl implements PostAdminService {

    private final BbsPostMapper postMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostAdminSummary> listAll(Long bbsId, int page, int size, String status, String keyword) {
        int offset = page * size;
        List<PostAdminSummary> content = postMapper.listForAdmin(bbsId, status, keyword, offset, size);
        long total = postMapper.countForAdmin(bbsId, status, keyword);
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public PostAdminSummary changeStatus(Long id, String status) {
        // 존재 확인 (deleted_at IS NULL 조건)
        postMapper.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        postMapper.updateStatusByAdmin(id, status);
        return postMapper.findAdminSummaryById(id).orElseThrow(() -> new PostNotFoundException(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        postMapper.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        postMapper.deleteById(id);
    }
}
