package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.FaqCategoryCount;
import kr.co.ircp.cms.domain.board.dto.FaqCreateRequest;
import kr.co.ircp.cms.domain.board.dto.FaqDetail;
import kr.co.ircp.cms.domain.board.dto.FaqReorderRequest;
import kr.co.ircp.cms.domain.board.dto.FaqSummary;
import kr.co.ircp.cms.domain.board.dto.FaqUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.Faq;
import kr.co.ircp.cms.domain.board.exception.FaqNotFoundException;
import kr.co.ircp.cms.domain.board.repository.FaqMapper;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * FAQ 서비스 구현체.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색
 *
 * // @MX:NOTE: [AUTO] FAQ 카테고리 통계, 페이징 검색, 일괄 정렬 변경 책임 담당.
 * // @MX:SPEC: REQ-BOARD-007
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqServiceImpl implements FaqService {

    private final FaqMapper faqMapper;
    private final HtmlSanitizer htmlSanitizer;

    @Override
    public PageResponse<FaqSummary> listFaqs(String category, String keyword, int page, int size) {
        int offset = page * size;
        List<Faq> faqs = faqMapper.findWithFilters(category, keyword, offset, size);
        long total = faqMapper.countWithFilters(category, keyword);
        List<FaqSummary> content = faqs.stream().map(this::toSummary).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public FaqDetail getFaq(Long id) {
        Faq faq = faqMapper.findById(id).orElseThrow(() -> new FaqNotFoundException(id));
        // 조회수 증가 (트랜잭션 내 별도 쿼리)
        faqMapper.incrementViewCount(id);
        return toDetail(faq);
    }

    @Override
    public List<FaqCategoryCount> getCategories() {
        List<Map<String, Object>> rows = faqMapper.countByCategory();
        return rows.stream()
                .map(row -> new FaqCategoryCount(
                        (String) row.get("categoryCode"),
                        toLong(row.get("count"))
                ))
                .toList();
    }

    @Override
    @Transactional
    public FaqDetail createFaq(FaqCreateRequest request, Long createdBy) {
        // SPEC-CMS-SECURITY-XSS — RICH_TEXT 답변 Jsoup sanitize 적용
        String sanitizedAnswer = htmlSanitizer.sanitize(request.answerHtml());
        Faq faq = Faq.builder()
                .categoryCode(request.categoryCode())
                .question(request.question())
                .answerHtml(sanitizedAnswer)
                .answerText(stripHtml(sanitizedAnswer))
                .sortOrder(request.sortOrder())
                .status("PUBLISHED")
                .createdBy(createdBy)
                .build();
        faqMapper.insert(faq);
        return toDetail(faq);
    }

    @Override
    @Transactional
    public FaqDetail updateFaq(Long id, FaqUpdateRequest request) {
        Faq existing = faqMapper.findById(id).orElseThrow(() -> new FaqNotFoundException(id));
        if (request.categoryCode() != null) {
            existing.setCategoryCode(request.categoryCode());
        }
        if (request.question() != null) {
            existing.setQuestion(request.question());
        }
        if (request.answerHtml() != null) {
            // SPEC-CMS-SECURITY-XSS — 변경 시 RICH_TEXT 답변 Jsoup sanitize 적용
            String sanitizedAnswer = htmlSanitizer.sanitize(request.answerHtml());
            existing.setAnswerHtml(sanitizedAnswer);
            existing.setAnswerText(stripHtml(sanitizedAnswer));
        }
        if (request.sortOrder() != null) {
            existing.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            existing.setStatus(request.status());
        }
        faqMapper.update(existing);
        // 업데이트 후 최신 상태 재조회
        Faq refreshed = faqMapper.findById(id).orElseThrow(() -> new FaqNotFoundException(id));
        return toDetail(refreshed);
    }

    @Override
    @Transactional
    public void deleteFaq(Long id) {
        int affected = faqMapper.deleteById(id);
        if (affected == 0) {
            throw new FaqNotFoundException(id);
        }
    }

    @Override
    @Transactional
    public void reorderFaqs(FaqReorderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return;
        }
        faqMapper.batchUpdateSortOrder(request.items());
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────

    private FaqSummary toSummary(Faq f) {
        return new FaqSummary(
                f.getId(), f.getCategoryCode(), f.getQuestion(),
                f.getSortOrder(), f.getViewCount(), f.getStatus(),
                f.getCreatedAt()
        );
    }

    private FaqDetail toDetail(Faq f) {
        return new FaqDetail(
                f.getId(), f.getCategoryCode(), f.getQuestion(),
                f.getAnswerHtml(), f.getAnswerText(),
                f.getSortOrder(), f.getViewCount(), f.getStatus(),
                f.getCreatedAt(), f.getUpdatedAt()
        );
    }

    /** HTML 태그 제거. */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /** Object를 long으로 안전 변환 (DB 드라이버에 따라 BigDecimal/Long/Integer 가능). */
    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
