package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.FaqCategoryCount;
import kr.co.ircp.cms.domain.board.dto.FaqCreateRequest;
import kr.co.ircp.cms.domain.board.dto.FaqDetail;
import kr.co.ircp.cms.domain.board.dto.FaqReorderItem;
import kr.co.ircp.cms.domain.board.dto.FaqReorderRequest;
import kr.co.ircp.cms.domain.board.dto.FaqSummary;
import kr.co.ircp.cms.domain.board.dto.FaqUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.Faq;
import kr.co.ircp.cms.domain.board.exception.FaqNotFoundException;
import kr.co.ircp.cms.domain.board.repository.FaqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FaqService GREEN 단계 테스트.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FaqService GREEN 테스트 (REQ-BOARD-007)")
class FaqServiceTest {

    @Mock
    private FaqMapper faqMapper;

    private FaqService faqService;
    private final kr.co.ircp.cms.domain.board.util.HtmlSanitizer htmlSanitizer =
            new kr.co.ircp.cms.domain.board.util.HtmlSanitizer();

    @BeforeEach
    void setUp() {
        faqService = new FaqServiceImpl(faqMapper, htmlSanitizer);
    }

    // 공통 스텁 빌더 — 기본 상태의 PUBLISHED FAQ 생성
    private Faq stubFaq(long id, String categoryCode) {
        return Faq.builder()
                .id(id)
                .categoryCode(categoryCode)
                .question("질문 " + id)
                .answerHtml("<p>답변 " + id + "</p>")
                .answerText("답변 " + id)
                .sortOrder(1)
                .viewCount(0L)
                .status("PUBLISHED")
                .createdBy(100L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-Q-1: FAQ 목록 페이징/필터 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 목록 페이징 — page/size 기반 offset 계산 및 PageResponse 반환")
    void listFaqs_returnsPageResponse() {
        // arrange — page=1, size=10이면 offset=10
        when(faqMapper.findWithFilters(eq("GENERAL"), eq("배송"), eq(10), eq(10)))
                .thenReturn(List.of(stubFaq(1L, "GENERAL"), stubFaq(2L, "GENERAL")));
        when(faqMapper.countWithFilters(eq("GENERAL"), eq("배송"))).thenReturn(25L);

        // act
        PageResponse<FaqSummary> result = faqService.listFaqs("GENERAL", "배송", 1, 10);

        // assert
        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25L);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10) = 3
        verify(faqMapper).findWithFilters("GENERAL", "배송", 10, 10);
        verify(faqMapper).countWithFilters("GENERAL", "배송");
    }

    @Test
    @DisplayName("FAQ 목록 — null 카테고리/키워드를 그대로 매퍼에 전달")
    void listFaqs_emptyFilters_callsMapperWithNulls() {
        // arrange
        when(faqMapper.findWithFilters(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(stubFaq(1L, "GENERAL")));
        when(faqMapper.countWithFilters(isNull(), isNull())).thenReturn(1L);

        // act
        PageResponse<FaqSummary> result = faqService.listFaqs(null, null, 0, 20);

        // assert
        assertThat(result.content()).hasSize(1);
        verify(faqMapper).findWithFilters(null, null, 0, 20);
        verify(faqMapper).countWithFilters(null, null);
    }

    @Test
    @DisplayName("FAQ 목록 — 빈 결과면 totalElements=0 반환")
    void listFaqs_emptyResult_returnsEmptyPage() {
        // arrange
        when(faqMapper.findWithFilters(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(faqMapper.countWithFilters(isNull(), isNull())).thenReturn(0L);

        // act
        PageResponse<FaqSummary> result = faqService.listFaqs(null, null, 0, 20);

        // assert
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-Q-2: FAQ 단건 조회 + 조회수 증가
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 단건 조회 — 존재하는 ID는 조회수 증가 후 상세 반환")
    void getFaq_existingId_incrementsViewCountAndReturnsDetail() {
        // arrange
        Faq faq = stubFaq(1L, "GENERAL");
        when(faqMapper.findById(1L)).thenReturn(Optional.of(faq));

        // act
        FaqDetail result = faqService.getFaq(1L);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.categoryCode()).isEqualTo("GENERAL");
        verify(faqMapper).incrementViewCount(1L);
    }

    @Test
    @DisplayName("FAQ 단건 조회 — 존재하지 않는 ID는 FaqNotFoundException")
    void getFaq_nonExistentId_throwsFaqNotFoundException() {
        // arrange
        when(faqMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> faqService.getFaq(999L))
                .isInstanceOf(FaqNotFoundException.class);

        verify(faqMapper, never()).incrementViewCount(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-Q-3: 카테고리별 통계 + 안전한 long 변환
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 카테고리 통계 — Long/BigDecimal/String 모두 long으로 안전 변환")
    void getCategories_aggregatesAndConvertsToLong() {
        // arrange — DB 드라이버 종류에 따라 다른 숫자 타입이 반환될 수 있음
        Map<String, Object> row1 = new HashMap<>();
        row1.put("categoryCode", "GENERAL");
        row1.put("count", 10L); // Long 타입
        Map<String, Object> row2 = new HashMap<>();
        row2.put("categoryCode", "PAYMENT");
        row2.put("count", new BigDecimal("5")); // BigDecimal 타입 (Number 인터페이스)
        Map<String, Object> row3 = new HashMap<>();
        row3.put("categoryCode", "SHIPPING");
        row3.put("count", "3"); // String fallback
        when(faqMapper.countByCategory()).thenReturn(List.of(row1, row2, row3));

        // act
        List<FaqCategoryCount> result = faqService.getCategories();

        // assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).categoryCode()).isEqualTo("GENERAL");
        assertThat(result.get(0).count()).isEqualTo(10L);
        assertThat(result.get(1).categoryCode()).isEqualTo("PAYMENT");
        assertThat(result.get(1).count()).isEqualTo(5L);
        assertThat(result.get(2).categoryCode()).isEqualTo("SHIPPING");
        assertThat(result.get(2).count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("FAQ 카테고리 통계 — 빈 리스트 반환")
    void getCategories_emptyList_returnsEmpty() {
        // arrange
        when(faqMapper.countByCategory()).thenReturn(List.of());

        // act
        List<FaqCategoryCount> result = faqService.getCategories();

        // assert
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-C: FAQ 생성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 생성 — status=PUBLISHED 고정 및 answerHtml에서 HTML 태그 제거")
    void createFaq_setsStatusPublishedAndStripsHtml() {
        // arrange
        FaqCreateRequest request = new FaqCreateRequest(
                "GENERAL",
                "배송 기간이 얼마나 걸리나요?",
                "<p>답<b>변</b></p>",
                1
        );
        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);

        // act
        FaqDetail result = faqService.createFaq(request, 100L);

        // assert
        verify(faqMapper).insert(captor.capture());
        Faq inserted = captor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("PUBLISHED");
        assertThat(inserted.getAnswerText()).isEqualTo("답변");
        assertThat(inserted.getAnswerHtml()).isEqualTo("<p>답<b>변</b></p>");
        assertThat(result).isNotNull();
        assertThat(result.categoryCode()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("FAQ 생성 — 빌더로 만든 엔티티 모든 필드가 매퍼에 전달")
    void createFaq_callsInsertWithBuilderEntity() {
        // arrange
        FaqCreateRequest request = new FaqCreateRequest(
                "PAYMENT",
                "결제 수단은 무엇이 있나요?",
                "<p>카드 결제</p>",
                5
        );
        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);

        // act
        faqService.createFaq(request, 200L);

        // assert
        verify(faqMapper).insert(captor.capture());
        Faq inserted = captor.getValue();
        assertThat(inserted.getCategoryCode()).isEqualTo("PAYMENT");
        assertThat(inserted.getQuestion()).isEqualTo("결제 수단은 무엇이 있나요?");
        assertThat(inserted.getAnswerHtml()).isEqualTo("<p>카드 결제</p>");
        assertThat(inserted.getAnswerText()).isEqualTo("카드 결제");
        assertThat(inserted.getSortOrder()).isEqualTo(5);
        assertThat(inserted.getCreatedBy()).isEqualTo(200L);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-U: FAQ 부분 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 수정 — null 필드는 기존 값 유지 (부분 업데이트)")
    void updateFaq_partialUpdate_onlyChangedFieldsApplied() {
        // arrange — categoryCode와 sortOrder만 변경, 나머지는 null로 보존
        Faq existing = stubFaq(1L, "GENERAL");
        when(faqMapper.findById(1L)).thenReturn(Optional.of(existing));
        FaqUpdateRequest request = new FaqUpdateRequest(
                null, // categoryCode → 유지
                "수정된 질문",
                null, // answerHtml → 유지
                null, // sortOrder → 유지
                null  // status → 유지
        );
        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);

        // act
        faqService.updateFaq(1L, request);

        // assert
        verify(faqMapper).update(captor.capture());
        Faq updated = captor.getValue();
        assertThat(updated.getCategoryCode()).isEqualTo("GENERAL"); // 기존값 유지
        assertThat(updated.getQuestion()).isEqualTo("수정된 질문"); // 변경됨
        assertThat(updated.getAnswerHtml()).isEqualTo("<p>답변 1</p>"); // 기존값 유지
        assertThat(updated.getStatus()).isEqualTo("PUBLISHED"); // 기존값 유지
    }

    @Test
    @DisplayName("FAQ 수정 — 모든 필드 업데이트 후 재조회하여 최신 상태 반환")
    void updateFaq_allFieldsUpdated_refreshAndReturn() {
        // arrange
        Faq existing = stubFaq(1L, "GENERAL");
        Faq refreshed = Faq.builder()
                .id(1L).categoryCode("PAYMENT").question("새 질문")
                .answerHtml("<p>새 답변</p>").answerText("새 답변")
                .sortOrder(3).viewCount(0L).status("HIDDEN")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(faqMapper.findById(1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(refreshed));
        FaqUpdateRequest request = new FaqUpdateRequest(
                "PAYMENT", "새 질문", "<p>새 답변</p>", 3, "HIDDEN"
        );

        // act
        FaqDetail result = faqService.updateFaq(1L, request);

        // assert
        assertThat(result.categoryCode()).isEqualTo("PAYMENT");
        assertThat(result.question()).isEqualTo("새 질문");
        assertThat(result.answerText()).isEqualTo("새 답변");
        assertThat(result.sortOrder()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("HIDDEN");
        verify(faqMapper).update(any());
    }

    @Test
    @DisplayName("FAQ 수정 — 존재하지 않는 ID는 첫 findById에서 FaqNotFoundException")
    void updateFaq_nonExistentId_throwsAtFirstFind() {
        // arrange
        when(faqMapper.findById(999L)).thenReturn(Optional.empty());
        FaqUpdateRequest request = new FaqUpdateRequest(
                "GENERAL", "질문", "<p>답</p>", 1, "PUBLISHED"
        );

        // act + assert
        assertThatThrownBy(() -> faqService.updateFaq(999L, request))
                .isInstanceOf(FaqNotFoundException.class);

        verify(faqMapper, never()).update(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-D: FAQ 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 삭제 — affectedRows>0이면 정상 종료")
    void deleteFaq_affectedRows_succeeds() {
        // arrange
        when(faqMapper.deleteById(1L)).thenReturn(1);

        // act
        faqService.deleteFaq(1L);

        // assert
        verify(faqMapper).deleteById(1L);
    }

    @Test
    @DisplayName("FAQ 삭제 — affectedRows=0이면 FaqNotFoundException")
    void deleteFaq_affectedZero_throwsFaqNotFoundException() {
        // arrange
        when(faqMapper.deleteById(999L)).thenReturn(0);

        // act + assert
        assertThatThrownBy(() -> faqService.deleteFaq(999L))
                .isInstanceOf(FaqNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-007-R: 정렬 순서 일괄 변경
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("FAQ 정렬 순서 변경 — null/empty items면 매퍼 호출 없이 무시")
    void reorderFaqs_emptyItems_doesNothing() {
        // arrange + act — null 요청
        faqService.reorderFaqs(null);

        // arrange + act — empty items
        FaqReorderRequest emptyRequest = new FaqReorderRequest(List.of());
        faqService.reorderFaqs(emptyRequest);

        // assert
        verify(faqMapper, never()).batchUpdateSortOrder(any());
    }

    @Test
    @DisplayName("FAQ 정렬 순서 변경 — 유효한 항목들은 batchUpdateSortOrder 호출")
    void reorderFaqs_validItems_callsBatchUpdate() {
        // arrange
        List<FaqReorderItem> items = List.of(
                new FaqReorderItem(1L, 3),
                new FaqReorderItem(2L, 1),
                new FaqReorderItem(3L, 2)
        );
        FaqReorderRequest request = new FaqReorderRequest(items);

        // act
        faqService.reorderFaqs(request);

        // assert
        verify(faqMapper).batchUpdateSortOrder(items);
    }
}
