package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.QnaAnswerRequest;
import kr.co.ircp.cms.domain.board.dto.QnaCreateRequest;
import kr.co.ircp.cms.domain.board.dto.QnaDetail;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;
import kr.co.ircp.cms.domain.board.entity.Qna;
import kr.co.ircp.cms.domain.board.entity.QnaStatus;
import kr.co.ircp.cms.domain.board.exception.QnaNotFoundException;
import kr.co.ircp.cms.domain.board.repository.QnaMapper;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Q&A 서비스 구현체.
 * REQ-BOARD-008: 질문/답변 워크플로 + 비공개 접근 제어
 *
 * // @MX:NOTE: [AUTO] 비공개 Q&A 노출 제어 — 권한 없는 사용자에게는 존재 자체를 숨김(404 반환).
 * // @MX:SPEC: REQ-BOARD-008
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnaServiceImpl implements QnaService {

    private final QnaMapper qnaMapper;
    private final QnaNotificationService qnaNotificationService;
    private final HtmlSanitizer htmlSanitizer;

    @Override
    public PageResponse<QnaSummary> listQnas(
            String status,
            Boolean isPrivate,
            String keyword,
            int page,
            int size,
            Long requesterId,
            boolean isAdmin,
            boolean mine
    ) {
        int offset = page * size;
        List<Qna> qnas = qnaMapper.findWithFilters(status, isPrivate, requesterId, isAdmin, keyword, mine, offset, size);
        long total = qnaMapper.countWithFilters(status, isPrivate, requesterId, isAdmin, keyword, mine);
        List<QnaSummary> content = qnas.stream().map(this::toSummary).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public QnaDetail getQna(Long id, Long requesterId, boolean isAdmin) {
        Qna qna = qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        // 비공개 항목은 본인 또는 관리자만 조회 가능. 그 외에는 존재 자체를 숨김.
        if (qna.isPrivate() && !isAdmin && !Objects.equals(qna.getQuestionerId(), requesterId)) {
            throw new QnaNotFoundException(id);
        }
        return toDetail(qna);
    }

    @Override
    @Transactional
    public QnaDetail createQna(QnaCreateRequest request, Long questionerId) {
        // SPEC-CMS-SECURITY-XSS — RICH_TEXT 질문 Jsoup sanitize 적용
        String sanitizedQuestion = htmlSanitizer.sanitize(request.questionHtml());
        Qna qna = Qna.builder()
                .title(request.title())
                .questionHtml(sanitizedQuestion)
                .questionText(stripHtml(sanitizedQuestion))
                .questionerId(questionerId)
                .isPrivate(request.isPrivate())
                .status(QnaStatus.PENDING.name())
                // SPEC-CMS-AI-004: 요청 태그 반영 (null/미전송 시 빈 목록 — TypeHandler NPE 방지)
                .tags(request.tags() != null ? request.tags() : Collections.emptyList())
                .build();
        qnaMapper.insert(qna);
        return toDetail(qna);
    }

    @Override
    @Transactional
    public QnaDetail answerQna(Long id, QnaAnswerRequest request, Long answererId) {
        Qna existing = qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        // CLOSED 상태에는 답변할 수 없음
        if (QnaStatus.CLOSED.name().equals(existing.getStatus())) {
            throw new IllegalStateException("종료된 Q&A에는 답변을 등록할 수 없습니다.");
        }
        // SPEC-CMS-SECURITY-XSS — RICH_TEXT 답변 Jsoup sanitize 적용
        String sanitizedAnswer = htmlSanitizer.sanitize(request.answerHtml());
        String answerText = stripHtml(sanitizedAnswer);
        qnaMapper.updateAnswer(id, sanitizedAnswer, answerText, answererId);
        // 답변 완료 후 알림 발송 (PENDING → ANSWERED 전환 시에만)
        // REQ-BOARD-014-D: INAPP/EMAIL 채널 알림 (멱등성·재시도·옵트아웃)
        if (QnaStatus.PENDING.name().equals(existing.getStatus())) {
            qnaNotificationService.notifyAnswered(id, existing.getQuestionerId(), answererId);
        }
        Qna refreshed = qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        return toDetail(refreshed);
    }

    @Override
    @Transactional
    public void closeQna(Long id, Long requesterId, boolean isAdmin) {
        Qna existing = qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        // 질문자 본인 또는 관리자만 종료 가능
        if (!isAdmin && !Objects.equals(existing.getQuestionerId(), requesterId)) {
            throw new AccessDeniedException("Q&A 종료 권한이 없습니다.");
        }
        qnaMapper.updateStatus(id, QnaStatus.CLOSED.name());
    }

    @Override
    @Transactional
    public void deleteQna(Long id, Long requesterId, boolean isAdmin) {
        Qna existing = qnaMapper.findById(id).orElseThrow(() -> new QnaNotFoundException(id));
        if (isAdmin) {
            qnaMapper.deleteById(id);
            return;
        }
        // 질문자는 PENDING 상태일 때만 삭제 가능
        if (!Objects.equals(existing.getQuestionerId(), requesterId)) {
            throw new AccessDeniedException("Q&A 삭제 권한이 없습니다.");
        }
        if (!QnaStatus.PENDING.name().equals(existing.getStatus())) {
            throw new IllegalStateException("답변 후에는 삭제할 수 없습니다. 관리자에게 문의해 주세요.");
        }
        qnaMapper.deleteById(id);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────

    private QnaSummary toSummary(Qna q) {
        return new QnaSummary(
                q.getId(), q.getTitle(), q.getQuestionerId(),
                q.getStatus(), q.isPrivate(), q.getCreatedAt(), q.getTags()
        );
    }

    private QnaDetail toDetail(Qna q) {
        return new QnaDetail(
                q.getId(), q.getTitle(),
                q.getQuestionHtml(), q.getQuestionText(),
                q.getQuestionerId(),
                q.getAnswerHtml(), q.getAnswerText(),
                q.getAnswererId(), q.getAnsweredAt(),
                q.isPrivate(), q.getStatus(), q.getTags(),
                q.getCreatedAt(), q.getUpdatedAt()
        );
    }

    /** HTML 태그 제거. */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
