package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;
import kr.co.ircp.cms.domain.board.entity.Qna;
import kr.co.ircp.cms.domain.board.exception.QnaNotFoundException;
import kr.co.ircp.cms.domain.board.repository.QnaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Q&A 어드민 모더레이션 서비스 구현체.
 * SPEC-CMS-QNA-MODERATE-001
 */
@Service
@RequiredArgsConstructor
public class QnaAdminServiceImpl implements QnaAdminService {

    private final QnaMapper qnaMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QnaSummary> listAll(int page, int size, String status, String keyword) {
        int offset = page * size;
        List<Qna> rows = qnaMapper.listForAdmin(status, keyword, offset, size);
        long total = qnaMapper.countForAdmin(status, keyword);
        List<QnaSummary> content = rows.stream().map(this::toSummary).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public QnaSummary changeStatus(Long id, String status) {
        Qna qna = qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        qnaMapper.updateStatus(qna.getId(), status);
        return toSummary(qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id)));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        qnaMapper.deleteById(id);
    }

    private QnaSummary toSummary(Qna q) {
        return new QnaSummary(q.getId(), q.getTitle(), q.getQuestionerId(),
                q.getStatus(), q.isPrivate(), q.getCreatedAt(), q.getTags());
    }
}
