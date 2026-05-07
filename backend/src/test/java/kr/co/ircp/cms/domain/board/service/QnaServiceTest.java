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
import kr.co.ircp.cms.domain.board.service.QnaNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QnaService GREEN 단계 테스트.
 * REQ-BOARD-008: 질문/답변 워크플로 + 비공개 접근 제어
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QnaService GREEN 테스트 (REQ-BOARD-008)")
class QnaServiceTest {

    @Mock
    private QnaMapper qnaMapper;

    @Mock
    private QnaNotificationService qnaNotificationService;

    private QnaService qnaService;

    @BeforeEach
    void setUp() {
        qnaService = new QnaServiceImpl(qnaMapper, qnaNotificationService);
    }

    // 공통 스텁 빌더 — 기본 상태의 PENDING/공개 Q&A 생성
    private Qna stubQna(long id, Long questionerId, boolean isPrivate, String status) {
        return Qna.builder()
                .id(id)
                .title("질문 제목 " + id)
                .questionHtml("<p>질문 내용 " + id + "</p>")
                .questionText("질문 내용 " + id)
                .questionerId(questionerId)
                .isPrivate(isPrivate)
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-008-Q-1: Q&A 목록 페이징
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Q&A 목록 페이징 — 필터 전달 및 PageResponse 반환")
    void listQnas_returnsPageResponse() {
        // arrange — page=2, size=10이면 offset=20
        List<Qna> qnas = List.of(stubQna(1L, 100L, false, "PENDING"));
        when(qnaMapper.findWithFilters(eq("PENDING"), eq(false), eq(100L), eq(false), eq("배송"), eq(20), eq(10)))
                .thenReturn(qnas);
        when(qnaMapper.countWithFilters(eq("PENDING"), eq(false), eq(100L), eq(false), eq("배송")))
                .thenReturn(1L);

        // act
        PageResponse<QnaSummary> result = qnaService.listQnas(
                "PENDING", false, "배송", 2, 10, 100L, false
        );

        // assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(qnaMapper).findWithFilters("PENDING", false, 100L, false, "배송", 20, 10);
        verify(qnaMapper).countWithFilters("PENDING", false, 100L, false, "배송");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-008-Q-2: 단건 조회 + 비공개 접근 제어 (보안 핵심)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Q&A 단건 조회 — 공개 항목은 누구나 조회 가능")
    void getQna_publicQna_returnsDetail() {
        // arrange — isPrivate=false인 Q&A
        Qna qna = stubQna(1L, 100L, false, "PENDING");
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(qna));

        // act — 다른 사용자(200L)가 조회
        QnaDetail result = qnaService.getQna(1L, 200L, false);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.isPrivate()).isFalse();
    }

    @Test
    @DisplayName("Q&A 단건 조회 — 비공개 항목은 본인이 조회 가능")
    void getQna_privateQna_owner_returnsDetail() {
        // arrange — isPrivate=true, questionerId=100L
        Qna qna = stubQna(1L, 100L, true, "PENDING");
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(qna));

        // act — 본인(100L) 조회
        QnaDetail result = qnaService.getQna(1L, 100L, false);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.questionerId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Q&A 단건 조회 — 비공개 항목도 관리자는 조회 가능")
    void getQna_privateQna_admin_returnsDetail() {
        // arrange
        Qna qna = stubQna(1L, 100L, true, "PENDING");
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(qna));

        // act — 다른 사용자(999L)지만 isAdmin=true
        QnaDetail result = qnaService.getQna(1L, 999L, true);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Q&A 단건 조회 보안 — 비공개 + 비본인 + 비관리자는 NotFound로 위장 (존재 자체 숨김)")
    void getQna_privateQna_otherUser_throwsAsNotFound() {
        // arrange — isPrivate=true, questionerId=100L
        Qna qna = stubQna(1L, 100L, true, "PENDING");
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(qna));

        // act + assert — 다른 사용자(200L) + 비관리자: 403이 아닌 404로 위장하여 존재 자체를 숨김
        assertThatThrownBy(() -> qnaService.getQna(1L, 200L, false))
                .isInstanceOf(QnaNotFoundException.class);
    }

    @Test
    @DisplayName("Q&A 단건 조회 — 존재하지 않는 ID는 QnaNotFoundException")
    void getQna_nonExistentId_throwsQnaNotFoundException() {
        // arrange
        when(qnaMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> qnaService.getQna(999L, 100L, false))
                .isInstanceOf(QnaNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-008-C: Q&A 질문 등록
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Q&A 질문 등록 — status=PENDING 고정 및 questionHtml에서 HTML 태그 제거")
    void createQna_setsStatusPending() {
        // arrange
        QnaCreateRequest request = new QnaCreateRequest(
                "환불 문의",
                "<p>환불<b>은</b> 가능한가요?</p>",
                true
        );
        ArgumentCaptor<Qna> captor = ArgumentCaptor.forClass(Qna.class);

        // act
        QnaDetail result = qnaService.createQna(request, 100L);

        // assert
        verify(qnaMapper).insert(captor.capture());
        Qna inserted = captor.getValue();
        assertThat(inserted.getStatus()).isEqualTo(QnaStatus.PENDING.name());
        assertThat(inserted.getQuestionText()).isEqualTo("환불은 가능한가요?");
        assertThat(inserted.getQuestionHtml()).isEqualTo("<p>환불<b>은</b> 가능한가요?</p>");
        assertThat(inserted.getQuestionerId()).isEqualTo(100L);
        assertThat(inserted.isPrivate()).isTrue();
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("환불 문의");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-008-A: Q&A 답변 등록 워크플로
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Q&A 답변 등록 — PENDING 상태에서 답변 정상 등록 후 ANSWERED로 전환")
    void answerQna_pendingStatus_updatesAnswer() {
        // arrange
        Qna existing = stubQna(1L, 100L, false, "PENDING");
        Qna answered = Qna.builder()
                .id(1L).title("질문 제목 1")
                .questionHtml("<p>질문 내용 1</p>").questionText("질문 내용 1")
                .questionerId(100L).isPrivate(false)
                .answerHtml("<p>답변입니다</p>").answerText("답변입니다")
                .answererId(999L).answeredAt(Instant.now())
                .status(QnaStatus.ANSWERED.name())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(qnaMapper.findById(1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(answered));
        QnaAnswerRequest request = new QnaAnswerRequest("<p>답변입니다</p>");

        // act
        QnaDetail result = qnaService.answerQna(1L, request, 999L);

        // assert
        verify(qnaMapper).updateAnswer(eq(1L), eq("<p>답변입니다</p>"), eq("답변입니다"), eq(999L));
        assertThat(result.status()).isEqualTo(QnaStatus.ANSWERED.name());
        assertThat(result.answerText()).isEqualTo("답변입니다");
        assertThat(result.answererId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("Q&A 답변 등록 — CLOSED 상태 Q&A에는 답변 차단 (IllegalStateException)")
    void answerQna_closedStatus_throwsIllegalStateException() {
        // arrange — 이미 종료된 Q&A
        Qna closed = stubQna(1L, 100L, false, QnaStatus.CLOSED.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(closed));
        QnaAnswerRequest request = new QnaAnswerRequest("<p>답변</p>");

        // act + assert
        assertThatThrownBy(() -> qnaService.answerQna(1L, request, 999L))
                .isInstanceOf(IllegalStateException.class);

        verify(qnaMapper, never()).updateAnswer(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Q&A 답변 등록 — 존재하지 않는 ID는 QnaNotFoundException")
    void answerQna_nonExistentId_throwsQnaNotFoundException() {
        // arrange
        when(qnaMapper.findById(999L)).thenReturn(Optional.empty());
        QnaAnswerRequest request = new QnaAnswerRequest("<p>답변</p>");

        // act + assert
        assertThatThrownBy(() -> qnaService.answerQna(999L, request, 999L))
                .isInstanceOf(QnaNotFoundException.class);

        verify(qnaMapper, never()).updateAnswer(anyLong(), anyString(), anyString(), anyLong());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-008-X: Q&A 종료 권한 제어
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Q&A 종료 — 질문자 본인이 종료 가능")
    void closeQna_owner_succeeds() {
        // arrange — questionerId=100L
        Qna existing = stubQna(1L, 100L, false, QnaStatus.ANSWERED.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act — 본인(100L) 종료
        qnaService.closeQna(1L, 100L, false);

        // assert
        verify(qnaMapper).updateStatus(1L, QnaStatus.CLOSED.name());
    }

    @Test
    @DisplayName("Q&A 종료 — 관리자가 종료 가능")
    void closeQna_admin_succeeds() {
        // arrange
        Qna existing = stubQna(1L, 100L, false, QnaStatus.ANSWERED.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act — 다른 사용자(999L)지만 isAdmin=true
        qnaService.closeQna(1L, 999L, true);

        // assert
        verify(qnaMapper).updateStatus(1L, QnaStatus.CLOSED.name());
    }

    @Test
    @DisplayName("Q&A 종료 — 본인 아님 + 비관리자는 AccessDeniedException")
    void closeQna_otherUser_throwsAccessDeniedException() {
        // arrange — questionerId=100L
        Qna existing = stubQna(1L, 100L, false, QnaStatus.ANSWERED.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act + assert — 다른 사용자(200L) + 비관리자
        assertThatThrownBy(() -> qnaService.closeQna(1L, 200L, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(qnaMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("Q&A 종료 — 존재하지 않는 ID는 QnaNotFoundException")
    void closeQna_nonExistentId_throwsQnaNotFoundException() {
        // arrange
        when(qnaMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> qnaService.closeQna(999L, 100L, false))
                .isInstanceOf(QnaNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-008-D: Q&A 삭제 권한·상태 제어
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Q&A 삭제 — 관리자는 어떤 상태든 무제약 삭제")
    void deleteQna_admin_unconditionalDelete() {
        // arrange — ANSWERED 상태 + 다른 사용자가 작성한 Q&A
        Qna existing = stubQna(1L, 100L, false, QnaStatus.ANSWERED.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act — 관리자(999L) + isAdmin=true
        qnaService.deleteQna(1L, 999L, true);

        // assert
        verify(qnaMapper).deleteById(1L);
    }

    @Test
    @DisplayName("Q&A 삭제 — 질문자 본인 + PENDING 상태는 삭제 가능")
    void deleteQna_owner_pendingStatus_succeeds() {
        // arrange — questionerId=100L, status=PENDING
        Qna existing = stubQna(1L, 100L, false, QnaStatus.PENDING.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act — 본인(100L) 삭제
        qnaService.deleteQna(1L, 100L, false);

        // assert
        verify(qnaMapper).deleteById(1L);
    }

    @Test
    @DisplayName("Q&A 삭제 — 본인이라도 답변 완료(ANSWERED) 후에는 IllegalStateException")
    void deleteQna_owner_answeredStatus_throwsIllegalStateException() {
        // arrange — 본인이지만 이미 답변 완료된 상태
        Qna existing = stubQna(1L, 100L, false, QnaStatus.ANSWERED.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act + assert
        assertThatThrownBy(() -> qnaService.deleteQna(1L, 100L, false))
                .isInstanceOf(IllegalStateException.class);

        verify(qnaMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Q&A 삭제 — 본인 아님 + 비관리자는 AccessDeniedException")
    void deleteQna_otherUser_throwsAccessDeniedException() {
        // arrange — questionerId=100L
        Qna existing = stubQna(1L, 100L, false, QnaStatus.PENDING.name());
        when(qnaMapper.findById(1L)).thenReturn(Optional.of(existing));

        // act + assert — 다른 사용자(200L) + 비관리자
        assertThatThrownBy(() -> qnaService.deleteQna(1L, 200L, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(qnaMapper, never()).deleteById(anyLong());
    }
}
