package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.SurveyAnswerRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyCreateRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyDetail;
import kr.co.ircp.cms.domain.board.dto.SurveyQuestionRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyResultDto;
import kr.co.ircp.cms.domain.board.dto.SurveySubmitRequest;
import kr.co.ircp.cms.domain.board.dto.SurveySummary;
import kr.co.ircp.cms.domain.board.dto.SurveyUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.Survey;
import kr.co.ircp.cms.domain.board.entity.SurveyQuestion;
import kr.co.ircp.cms.domain.board.entity.SurveyResponse;
import kr.co.ircp.cms.domain.board.exception.SurveyNotFoundException;
import kr.co.ircp.cms.domain.board.exception.SurveyPeriodInvalidException;
import kr.co.ircp.cms.domain.board.repository.SurveyAnswerMapper;
import kr.co.ircp.cms.domain.board.repository.SurveyMapper;
import kr.co.ircp.cms.domain.board.repository.SurveyQuestionMapper;
import kr.co.ircp.cms.domain.board.repository.SurveyResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SurveyService GREEN 단계 테스트.
 * REQ-BOARD-013: 설문 CRUD + 응답 제출 + 결과 통계
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SurveyService GREEN 테스트 (REQ-BOARD-013)")
class SurveyServiceTest {

    @Mock
    private SurveyMapper surveyMapper;

    @Mock
    private SurveyQuestionMapper surveyQuestionMapper;

    @Mock
    private SurveyResponseMapper surveyResponseMapper;

    @Mock
    private SurveyAnswerMapper surveyAnswerMapper;

    private SurveyService service;
    private final kr.co.ircp.cms.domain.board.util.HtmlSanitizer htmlSanitizer =
            new kr.co.ircp.cms.domain.board.util.HtmlSanitizer();

    @BeforeEach
    void setUp() {
        service = new SurveyServiceImpl(
                surveyMapper,
                surveyQuestionMapper,
                surveyResponseMapper,
                surveyAnswerMapper,
                htmlSanitizer
        );
    }

    // ─── 공통 스텁 빌더 ───────────────────────────────────────────────────

    /** 기본 상태(OPEN, 익명 false, 한도 없음, 기간 ±1시간)의 Survey 생성. */
    private Survey stubSurvey(long id) {
        Instant now = Instant.now();
        return Survey.builder()
                .id(id)
                .title("설문 " + id)
                .descriptionHtml("<p>설문 설명 " + id + "</p>")
                .descriptionText("설문 설명 " + id)
                .startAt(now.minusSeconds(3600))
                .endAt(now.plusSeconds(3600))
                .isAnonymous(false)
                .maxResponses(null)
                .responseCount(0)
                .status("OPEN")
                .createdBy(100L)
                .createdAt(now)
                .build();
    }

    /** 특정 상태/기간/익명/한도/응답수로 커스터마이즈된 Survey 생성. */
    private Survey stubSurvey(long id, String status, Instant startAt, Instant endAt,
                              boolean anonymous, Integer maxResponses, int responseCount) {
        return Survey.builder()
                .id(id)
                .title("설문 " + id)
                .descriptionHtml("<p>설명</p>")
                .descriptionText("설명")
                .startAt(startAt)
                .endAt(endAt)
                .isAnonymous(anonymous)
                .maxResponses(maxResponses)
                .responseCount(responseCount)
                .status(status)
                .createdBy(100L)
                .createdAt(Instant.now())
                .build();
    }

    private SurveyQuestion stubQuestion(long id, long surveyId, String type, int order) {
        return SurveyQuestion.builder()
                .id(id)
                .surveyId(surveyId)
                .questionText("질문 " + id)
                .questionType(type)
                .required(true)
                .sortOrder(order)
                .options("SINGLE".equals(type) || "MULTI".equals(type) ? "[\"A\",\"B\"]" : null)
                .build();
    }

    private SurveyQuestionRequest stubQuestionRequest(String type, int order) {
        return new SurveyQuestionRequest(
                "질문 " + order,
                type,
                true,
                order,
                "SINGLE".equals(type) || "MULTI".equals(type) ? "[\"A\",\"B\"]" : null
        );
    }

    /** 집계 row 한 건을 생성하는 헬퍼. 테스트마다 동적으로 키를 채워 넣는다. */
    private Map<String, Object> aggregateRow(long questionId, String questionType, String questionText,
                                             long totalResponses, long answerCount,
                                             String answerOptions, Integer answerRating, String answerDate) {
        Map<String, Object> row = new HashMap<>();
        row.put("question_id", questionId);
        row.put("question_text", questionText);
        row.put("question_type", questionType);
        row.put("total_responses", totalResponses);
        row.put("answer_count", answerCount);
        row.put("answer_options", answerOptions);
        row.put("answer_rating", answerRating);
        row.put("answer_date", answerDate);
        return row;
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-Q: 설문 목록 페이징 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("설문 목록 — page=1/size=10이면 offset=10으로 조회 후 PageResponse 반환")
    void listSurveys_returnsPageResponse() {
        // arrange — page=1, size=10이면 offset=10
        when(surveyMapper.findWithFilters(eq("OPEN"), eq("만족도"), eq(10), eq(10)))
                .thenReturn(List.of(stubSurvey(1L), stubSurvey(2L)));
        when(surveyMapper.countWithFilters(eq("OPEN"), eq("만족도"))).thenReturn(25L);

        // act
        PageResponse<SurveySummary> result = service.listSurveys("OPEN", "만족도", 1, 10);

        // assert
        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25L);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10) = 3
        verify(surveyMapper).findWithFilters("OPEN", "만족도", 10, 10);
        verify(surveyMapper).countWithFilters("OPEN", "만족도");
    }

    @Test
    @DisplayName("설문 목록 — 빈 결과면 totalElements=0, totalPages=0 반환")
    void listSurveys_emptyResult_returnsEmptyPage() {
        // arrange
        when(surveyMapper.findWithFilters(any(), any(), eq(0), eq(20))).thenReturn(List.of());
        when(surveyMapper.countWithFilters(any(), any())).thenReturn(0L);

        // act
        PageResponse<SurveySummary> result = service.listSurveys(null, null, 0, 20);

        // assert
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-Q: 설문 단건 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("설문 단건 조회 — 존재하는 ID는 질문 목록을 포함한 SurveyDetail 반환")
    void getSurvey_existingId_returnsDetailWithQuestions() {
        // arrange
        Survey survey = stubSurvey(1L);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));
        when(surveyQuestionMapper.findBySurveyId(1L)).thenReturn(List.of(
                stubQuestion(11L, 1L, "SINGLE", 1),
                stubQuestion(12L, 1L, "TEXT", 2)
        ));

        // act
        SurveyDetail result = service.getSurvey(1L);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("설문 1");
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.questions()).hasSize(2);
        assertThat(result.questions().get(0).id()).isEqualTo(11L);
        assertThat(result.questions().get(0).questionType()).isEqualTo("SINGLE");
        assertThat(result.questions().get(1).questionType()).isEqualTo("TEXT");
        verify(surveyMapper).findById(1L);
        verify(surveyQuestionMapper).findBySurveyId(1L);
    }

    @Test
    @DisplayName("설문 단건 조회 — 존재하지 않는 ID는 SurveyNotFoundException")
    void getSurvey_nonExistentId_throwsSurveyNotFoundException() {
        // arrange
        when(surveyMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getSurvey(999L))
                .isInstanceOf(SurveyNotFoundException.class);

        verify(surveyQuestionMapper, never()).findBySurveyId(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-C: 설문 신규 등록
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("설문 생성 — status=DRAFT, descriptionText null이면 stripHtml(html) 결과로 저장")
    void createSurvey_setsStatusDraftAndStripsHtml() {
        // arrange — INSERT 시 id 부여 시뮬레이션
        ArgumentCaptor<Survey> surveyCaptor = ArgumentCaptor.forClass(Survey.class);
        doAnswer(invocation -> {
            Survey s = invocation.getArgument(0);
            s.setId(101L);
            return null;
        }).when(surveyMapper).insert(any(Survey.class));

        // 재조회용 스텁
        Survey saved = stubSurvey(101L);
        when(surveyMapper.findById(101L)).thenReturn(Optional.of(saved));
        when(surveyQuestionMapper.findBySurveyId(101L)).thenReturn(List.of());

        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        SurveyCreateRequest req = new SurveyCreateRequest(
                "신규 설문",
                "<p>설명 <b>HTML</b></p>",
                null, // descriptionText null → stripHtml 적용 대상
                start,
                end,
                false,
                100,
                List.of(stubQuestionRequest("SINGLE", 1))
        );

        // act
        SurveyDetail result = service.createSurvey(req, 100L);

        // assert
        assertThat(result).isNotNull();
        verify(surveyMapper).insert(surveyCaptor.capture());
        Survey inserted = surveyCaptor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getTitle()).isEqualTo("신규 설문");
        assertThat(inserted.getDescriptionHtml()).isEqualTo("<p>설명 <b>HTML</b></p>");
        // stripHtml: <p>설명 <b>HTML</b></p> → "설명 HTML"
        assertThat(inserted.getDescriptionText()).isEqualTo("설명 HTML");
        assertThat(inserted.getCreatedBy()).isEqualTo(100L);
        assertThat(inserted.isAnonymous()).isFalse();
        assertThat(inserted.getMaxResponses()).isEqualTo(100);
    }

    @Test
    @DisplayName("설문 생성 — questions 비어있지 않으면 surveyQuestionMapper.insertBatch 호출")
    void createSurvey_withQuestions_insertsBatch() {
        // arrange
        doAnswer(invocation -> {
            Survey s = invocation.getArgument(0);
            s.setId(102L);
            return null;
        }).when(surveyMapper).insert(any(Survey.class));
        when(surveyMapper.findById(102L)).thenReturn(Optional.of(stubSurvey(102L)));
        when(surveyQuestionMapper.findBySurveyId(102L)).thenReturn(List.of());

        List<SurveyQuestionRequest> questions = List.of(
                stubQuestionRequest("SINGLE", 1),
                stubQuestionRequest("RATING", 2)
        );
        SurveyCreateRequest req = new SurveyCreateRequest(
                "설문", "<p>설명</p>", "설명",
                Instant.now(), Instant.now().plusSeconds(3600),
                false, null, questions
        );

        // act
        service.createSurvey(req, 100L);

        // assert — 질문 일괄 INSERT 호출 검증
        verify(surveyQuestionMapper).insertBatch(102L, questions);
    }

    @Test
    @DisplayName("설문 생성 — questions가 빈 리스트면 insertBatch 미호출")
    void createSurvey_emptyQuestions_skipsBatch() {
        // arrange
        doAnswer(invocation -> {
            Survey s = invocation.getArgument(0);
            s.setId(103L);
            return null;
        }).when(surveyMapper).insert(any(Survey.class));
        when(surveyMapper.findById(103L)).thenReturn(Optional.of(stubSurvey(103L)));
        when(surveyQuestionMapper.findBySurveyId(103L)).thenReturn(List.of());

        SurveyCreateRequest req = new SurveyCreateRequest(
                "설문", "<p>설명</p>", "설명",
                Instant.now(), Instant.now().plusSeconds(3600),
                false, null, List.of() // 빈 리스트
        );

        // act
        service.createSurvey(req, 100L);

        // assert
        verify(surveyQuestionMapper, never()).insertBatch(any(), any());
    }

    @Test
    @DisplayName("설문 생성 — questions가 null이면 insertBatch 미호출")
    void createSurvey_nullQuestions_skipsBatch() {
        // arrange
        doAnswer(invocation -> {
            Survey s = invocation.getArgument(0);
            s.setId(104L);
            return null;
        }).when(surveyMapper).insert(any(Survey.class));
        when(surveyMapper.findById(104L)).thenReturn(Optional.of(stubSurvey(104L)));
        when(surveyQuestionMapper.findBySurveyId(104L)).thenReturn(List.of());

        SurveyCreateRequest req = new SurveyCreateRequest(
                "설문", "<p>설명</p>", "설명",
                Instant.now(), Instant.now().plusSeconds(3600),
                false, null, null // null
        );

        // act
        service.createSurvey(req, 100L);

        // assert
        verify(surveyQuestionMapper, never()).insertBatch(any(), any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-U: 설문 부분 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("설문 수정 — 존재하는 ID는 surveyMapper.update 호출")
    void updateSurvey_existingId_callsUpdate() {
        // arrange
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyQuestionMapper.findBySurveyId(1L)).thenReturn(List.of());

        SurveyUpdateRequest req = new SurveyUpdateRequest(
                "수정된 제목", null, null, null, null, null, null, null, null
        );

        // act
        service.updateSurvey(1L, req);

        // assert
        verify(surveyMapper).update(1L, req);
    }

    @Test
    @DisplayName("설문 수정 — questions 제공 시 deleteBySurveyId + insertBatch 호출")
    void updateSurvey_withQuestions_deletesAndReinserts() {
        // arrange
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyQuestionMapper.findBySurveyId(1L)).thenReturn(List.of());

        List<SurveyQuestionRequest> newQuestions = List.of(
                stubQuestionRequest("MULTI", 1)
        );
        SurveyUpdateRequest req = new SurveyUpdateRequest(
                null, null, null, null, null, null, null, null, newQuestions
        );

        // act
        service.updateSurvey(1L, req);

        // assert — 기존 질문 삭제 후 재삽입 순서로 호출
        InOrder order = inOrder(surveyQuestionMapper);
        order.verify(surveyQuestionMapper).deleteBySurveyId(1L);
        order.verify(surveyQuestionMapper).insertBatch(1L, newQuestions);
    }

    @Test
    @DisplayName("설문 수정 — questions=빈 리스트면 deleteBySurveyId만 호출, insertBatch 미호출")
    void updateSurvey_emptyQuestions_deletesOnly() {
        // arrange
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyQuestionMapper.findBySurveyId(1L)).thenReturn(List.of());

        SurveyUpdateRequest req = new SurveyUpdateRequest(
                null, null, null, null, null, null, null, null, List.of()
        );

        // act
        service.updateSurvey(1L, req);

        // assert
        verify(surveyQuestionMapper).deleteBySurveyId(1L);
        verify(surveyQuestionMapper, never()).insertBatch(any(), any());
    }

    @Test
    @DisplayName("설문 수정 — questions=null이면 기존 질문 유지 (deleteBySurveyId 미호출)")
    void updateSurvey_nullQuestions_keepsExisting() {
        // arrange
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyQuestionMapper.findBySurveyId(1L)).thenReturn(List.of());

        SurveyUpdateRequest req = new SurveyUpdateRequest(
                "새 제목", null, null, null, null, null, null, null, null
        );

        // act
        service.updateSurvey(1L, req);

        // assert
        verify(surveyQuestionMapper, never()).deleteBySurveyId(any());
        verify(surveyQuestionMapper, never()).insertBatch(any(), any());
    }

    @Test
    @DisplayName("설문 수정 — 존재하지 않는 ID는 SurveyNotFoundException")
    void updateSurvey_nonExistentId_throwsSurveyNotFoundException() {
        // arrange
        when(surveyMapper.findById(999L)).thenReturn(Optional.empty());

        SurveyUpdateRequest req = new SurveyUpdateRequest(
                "제목", null, null, null, null, null, null, null, null
        );

        // act + assert
        assertThatThrownBy(() -> service.updateSurvey(999L, req))
                .isInstanceOf(SurveyNotFoundException.class);

        verify(surveyMapper, never()).update(any(), any());
        verify(surveyQuestionMapper, never()).deleteBySurveyId(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-D: 설문 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("설문 삭제 — 존재하는 ID는 softDelete 호출")
    void deleteSurvey_existingId_softDeletes() {
        // arrange
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));

        // act
        service.deleteSurvey(1L);

        // assert
        verify(surveyMapper).softDelete(1L);
    }

    @Test
    @DisplayName("설문 삭제 — 존재하지 않는 ID는 SurveyNotFoundException")
    void deleteSurvey_nonExistentId_throwsSurveyNotFoundException() {
        // arrange
        when(surveyMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.deleteSurvey(999L))
                .isInstanceOf(SurveyNotFoundException.class);

        verify(surveyMapper, never()).softDelete(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-D-3: 응답 제출 (보안·무결성 핵심)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("응답 제출 — OPEN 설문/유효 기간이면 응답 INSERT + 답변 batch + markSubmitted + 카운트 증가 순서")
    void submitResponse_openSurveyValidPeriod_insertsResponseAndAnswers() {
        // arrange — status=OPEN, 기간 내, 한도 없음, 비익명
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.minusSeconds(3600), now.plusSeconds(3600),
                false, null, 0);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));
        when(surveyResponseMapper.findByUserAndSurvey(1L, 42L)).thenReturn(Optional.empty());

        // 응답 INSERT 시 id 부여 시뮬레이션
        doAnswer(invocation -> {
            SurveyResponse r = invocation.getArgument(0);
            r.setId(7001L);
            return null;
        }).when(surveyResponseMapper).insert(any(SurveyResponse.class));

        List<SurveyAnswerRequest> answers = List.of(
                new SurveyAnswerRequest(11L, "자유 응답", null, null, null),
                new SurveyAnswerRequest(12L, null, "[\"A\"]", null, null)
        );
        SurveySubmitRequest req = new SurveySubmitRequest(answers);

        // act
        service.submitResponse(1L, req, 42L, "ABC123");

        // assert — 호출 순서 검증
        InOrder order = inOrder(surveyResponseMapper, surveyAnswerMapper, surveyMapper);
        order.verify(surveyResponseMapper).insert(any(SurveyResponse.class));
        order.verify(surveyAnswerMapper).insertBatch(7001L, answers);
        order.verify(surveyResponseMapper).markSubmitted(7001L);
        order.verify(surveyMapper).incrementResponseCount(1L);
    }

    @Test
    @DisplayName("응답 제출 — status가 OPEN이 아니면 SurveyPeriodInvalidException (CLOSED)")
    void submitResponse_statusNotOpen_throwsPeriodInvalid() {
        // arrange — status=CLOSED
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "CLOSED",
                now.minusSeconds(3600), now.plusSeconds(3600),
                false, null, 0);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act + assert
        assertThatThrownBy(() -> service.submitResponse(1L, req, 42L, "IP"))
                .isInstanceOf(SurveyPeriodInvalidException.class);

        verify(surveyResponseMapper, never()).insert(any());
        verify(surveyAnswerMapper, never()).insertBatch(any(), any());
        verify(surveyMapper, never()).incrementResponseCount(any());
    }

    @Test
    @DisplayName("응답 제출 — 시작 전(now < startAt)이면 SurveyPeriodInvalidException")
    void submitResponse_beforeStartAt_throwsPeriodInvalid() {
        // arrange — startAt이 60초 후 (아직 시작 전)
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.plusSeconds(60), now.plusSeconds(3600),
                false, null, 0);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act + assert
        assertThatThrownBy(() -> service.submitResponse(1L, req, 42L, "IP"))
                .isInstanceOf(SurveyPeriodInvalidException.class);

        verify(surveyResponseMapper, never()).insert(any());
    }

    @Test
    @DisplayName("응답 제출 — 종료 후(now >= endAt)이면 SurveyPeriodInvalidException")
    void submitResponse_afterEndAt_throwsPeriodInvalid() {
        // arrange — endAt이 60초 전 (이미 종료, !now.isBefore(endAt) → true)
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.minusSeconds(3600), now.minusSeconds(60),
                false, null, 0);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act + assert
        assertThatThrownBy(() -> service.submitResponse(1L, req, 42L, "IP"))
                .isInstanceOf(SurveyPeriodInvalidException.class);

        verify(surveyResponseMapper, never()).insert(any());
    }

    @Test
    @DisplayName("응답 제출 — responseCount >= maxResponses면 \"응답 한도 초과\" 메시지로 SurveyPeriodInvalidException")
    void submitResponse_responseCountAtMaxLimit_throwsPeriodInvalid() {
        // arrange — OPEN + 유효 기간 + 한도 10/응답수 10 (정확히 한도 도달)
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.minusSeconds(3600), now.plusSeconds(3600),
                false, 10, 10);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act + assert
        assertThatThrownBy(() -> service.submitResponse(1L, req, 42L, "IP"))
                .isInstanceOf(SurveyPeriodInvalidException.class)
                .hasMessage("응답 한도 초과");

        verify(surveyResponseMapper, never()).insert(any());
    }

    @Test
    @DisplayName("응답 제출 — 익명 설문이면 respondentId가 제공되어도 INSERT 시 NULL로 강제")
    void submitResponse_anonymousSurvey_forcesRespondentIdNull() {
        // arrange — isAnonymous=true
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.minusSeconds(3600), now.plusSeconds(3600),
                true, null, 0);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));

        ArgumentCaptor<SurveyResponse> responseCaptor = ArgumentCaptor.forClass(SurveyResponse.class);
        doAnswer(invocation -> {
            SurveyResponse r = invocation.getArgument(0);
            r.setId(7002L);
            return null;
        }).when(surveyResponseMapper).insert(any(SurveyResponse.class));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act — respondentId=42 제공
        service.submitResponse(1L, req, 42L, "IP");

        // assert — 익명 설문이므로 respondentId가 NULL로 저장
        verify(surveyResponseMapper).insert(responseCaptor.capture());
        SurveyResponse inserted = responseCaptor.getValue();
        assertThat(inserted.getRespondentId()).isNull();
        assertThat(inserted.getSurveyId()).isEqualTo(1L);
        // 익명이면 중복 응답 검사도 수행하지 않음
        verify(surveyResponseMapper, never()).findByUserAndSurvey(any(), any());
    }

    @Test
    @DisplayName("응답 제출 — 비익명 설문에서 동일 사용자가 이미 응답했으면 \"이미 응답하셨습니다\" 예외")
    void submitResponse_nonAnonymousDuplicateResponse_throwsAlreadyResponded() {
        // arrange — 비익명 + 이미 응답 존재
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.minusSeconds(3600), now.plusSeconds(3600),
                false, null, 5);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));

        SurveyResponse existing = SurveyResponse.builder()
                .id(999L).surveyId(1L).respondentId(42L).build();
        when(surveyResponseMapper.findByUserAndSurvey(1L, 42L))
                .thenReturn(Optional.of(existing));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act + assert
        assertThatThrownBy(() -> service.submitResponse(1L, req, 42L, "IP"))
                .isInstanceOf(SurveyPeriodInvalidException.class)
                .hasMessage("이미 응답하셨습니다");

        verify(surveyResponseMapper, never()).insert(any());
        verify(surveyMapper, never()).incrementResponseCount(any());
    }

    @Test
    @DisplayName("응답 제출 — ipHash가 null이면 빈 문자열로 저장")
    void submitResponse_nullIpHash_storesEmptyString() {
        // arrange
        Instant now = Instant.now();
        Survey survey = stubSurvey(1L, "OPEN",
                now.minusSeconds(3600), now.plusSeconds(3600),
                false, null, 0);
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(survey));
        when(surveyResponseMapper.findByUserAndSurvey(1L, 42L)).thenReturn(Optional.empty());

        ArgumentCaptor<SurveyResponse> captor = ArgumentCaptor.forClass(SurveyResponse.class);
        doAnswer(invocation -> {
            SurveyResponse r = invocation.getArgument(0);
            r.setId(7003L);
            return null;
        }).when(surveyResponseMapper).insert(any(SurveyResponse.class));

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act — ipHash=null 전달
        service.submitResponse(1L, req, 42L, null);

        // assert
        verify(surveyResponseMapper).insert(captor.capture());
        assertThat(captor.getValue().getRespondentIpHash()).isEqualTo("");
    }

    @Test
    @DisplayName("응답 제출 — 존재하지 않는 설문이면 SurveyNotFoundException")
    void submitResponse_nonExistentSurvey_throwsSurveyNotFoundException() {
        // arrange
        when(surveyMapper.findById(999L)).thenReturn(Optional.empty());

        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(11L, "텍스트", null, null, null))
        );

        // act + assert
        assertThatThrownBy(() -> service.submitResponse(999L, req, 42L, "IP"))
                .isInstanceOf(SurveyNotFoundException.class);

        verify(surveyResponseMapper, never()).insert(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-013-D-5: 결과 통계 (질문 유형별 분기)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("결과 통계 — TEXT 질문은 \"응답\" 라벨 단일 항목 + 100% 비율")
    void getResults_textQuestion_returnsSingleResponseLabel() {
        // arrange — TEXT 질문, 응답 3건
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyAnswerMapper.aggregateByQuestion(1L)).thenReturn(List.of(
                aggregateRow(11L, "TEXT", "자유 응답?", 5L, 3L, null, null, null)
        ));

        // act
        SurveyResultDto result = service.getResults(1L);

        // assert
        assertThat(result.surveyId()).isEqualTo(1L);
        assertThat(result.totalResponses()).isEqualTo(5);
        assertThat(result.questions()).hasSize(1);
        SurveyResultDto.QuestionResult q = result.questions().get(0);
        assertThat(q.questionId()).isEqualTo(11L);
        assertThat(q.questionType()).isEqualTo("TEXT");
        assertThat(q.totalAnswers()).isEqualTo(3);
        assertThat(q.distribution()).hasSize(1);
        SurveyResultDto.DistributionItem item = q.distribution().get(0);
        assertThat(item.label()).isEqualTo("응답");
        assertThat(item.count()).isEqualTo(3L);
        assertThat(item.percentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("결과 통계 — RATING 질문은 1~5 모든 라벨이 채워지며 누락된 점수는 0으로 zero-fill")
    void getResults_ratingQuestion_zeroFillsMissingScores() {
        // arrange — RATING 질문, 응답 점수 3(=2건), 5(=4건)만 존재 → 1/2/4는 0으로 채워야 함
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyAnswerMapper.aggregateByQuestion(1L)).thenReturn(List.of(
                aggregateRow(11L, "RATING", "만족도?", 6L, 2L, null, 3, null),
                aggregateRow(11L, "RATING", "만족도?", 6L, 4L, null, 5, null)
        ));

        // act
        SurveyResultDto result = service.getResults(1L);

        // assert
        assertThat(result.questions()).hasSize(1);
        SurveyResultDto.QuestionResult q = result.questions().get(0);
        assertThat(q.questionType()).isEqualTo("RATING");
        assertThat(q.totalAnswers()).isEqualTo(6);

        // 1~5 라벨 모두 존재 (zero-fill)
        List<SurveyResultDto.DistributionItem> dist = q.distribution();
        assertThat(dist).hasSize(5);
        assertThat(dist).extracting(SurveyResultDto.DistributionItem::label)
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(dist).extracting(SurveyResultDto.DistributionItem::count)
                .containsExactly(0L, 0L, 2L, 0L, 4L);
    }

    @Test
    @DisplayName("결과 통계 — SINGLE 질문은 answer_options 문자열 기준으로 그룹핑")
    void getResults_singleQuestion_groupsByOptions() {
        // arrange — SINGLE 질문, 옵션 "A"(=3건), "B"(=2건)
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyAnswerMapper.aggregateByQuestion(1L)).thenReturn(List.of(
                aggregateRow(11L, "SINGLE", "선택?", 5L, 3L, "[\"A\"]", null, null),
                aggregateRow(11L, "SINGLE", "선택?", 5L, 2L, "[\"B\"]", null, null)
        ));

        // act
        SurveyResultDto result = service.getResults(1L);

        // assert
        SurveyResultDto.QuestionResult q = result.questions().get(0);
        assertThat(q.questionType()).isEqualTo("SINGLE");
        assertThat(q.totalAnswers()).isEqualTo(5);
        assertThat(q.distribution()).hasSize(2);
        assertThat(q.distribution()).extracting(SurveyResultDto.DistributionItem::label)
                .containsExactlyInAnyOrder("[\"A\"]", "[\"B\"]");
        // 비율 검증: A=60%, B=40%
        assertThat(q.distribution()).extracting(SurveyResultDto.DistributionItem::count)
                .containsExactlyInAnyOrder(3L, 2L);
    }

    @Test
    @DisplayName("결과 통계 — DATE 질문은 answer_date 문자열 기준으로 그룹핑")
    void getResults_dateQuestion_groupsByDate() {
        // arrange — DATE 질문, 날짜 2건
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyAnswerMapper.aggregateByQuestion(1L)).thenReturn(List.of(
                aggregateRow(11L, "DATE", "방문일?", 4L, 3L, null, null, "2025-06-01"),
                aggregateRow(11L, "DATE", "방문일?", 4L, 1L, null, null, "2025-06-02")
        ));

        // act
        SurveyResultDto result = service.getResults(1L);

        // assert
        SurveyResultDto.QuestionResult q = result.questions().get(0);
        assertThat(q.questionType()).isEqualTo("DATE");
        assertThat(q.totalAnswers()).isEqualTo(4);
        assertThat(q.distribution()).hasSize(2);
        assertThat(q.distribution()).extracting(SurveyResultDto.DistributionItem::label)
                .containsExactlyInAnyOrder("2025-06-01", "2025-06-02");
        assertThat(q.distribution()).extracting(SurveyResultDto.DistributionItem::count)
                .containsExactlyInAnyOrder(3L, 1L);
    }

    @Test
    @DisplayName("결과 통계 — 집계 결과가 비어있으면 questions=빈 리스트, totalResponses=0")
    void getResults_emptyAggregation_returnsEmptyResults() {
        // arrange
        when(surveyMapper.findById(1L)).thenReturn(Optional.of(stubSurvey(1L)));
        when(surveyAnswerMapper.aggregateByQuestion(1L)).thenReturn(List.of());

        // act
        SurveyResultDto result = service.getResults(1L);

        // assert
        assertThat(result.surveyId()).isEqualTo(1L);
        assertThat(result.totalResponses()).isZero();
        assertThat(result.questions()).isEmpty();
    }

    @Test
    @DisplayName("결과 통계 — 존재하지 않는 설문이면 SurveyNotFoundException")
    void getResults_nonExistentSurvey_throwsSurveyNotFoundException() {
        // arrange
        when(surveyMapper.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getResults(999L))
                .isInstanceOf(SurveyNotFoundException.class);

        verify(surveyAnswerMapper, never()).aggregateByQuestion(any());
    }
}
