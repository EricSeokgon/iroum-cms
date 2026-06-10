package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;
import kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게시글 버전 히스토리 read 전용 서비스 구현체.
 * SPEC-CMS-POST-HISTORY-001 REQ-PH-001~005
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHistoryServiceImpl implements PostHistoryService {

    private final BbsPostHistoryMapper bbsPostHistoryMapper;

    @Override
    public PageResponse<PostHistoryItem> getHistory(Long postId, int page, int size) {
        int offset = page * size;
        List<PostHistoryItem> content = bbsPostHistoryMapper.findPageByPostId(postId, offset, size);
        long total = bbsPostHistoryMapper.countByPostId(postId);
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PostHistoryDetail getVersion(Long postId, int version) {
        return bbsPostHistoryMapper.findByPostIdAndVersion(postId, version)
                .orElseThrow(() -> new PostHistoryVersionNotFoundException(postId, version));
    }
}
