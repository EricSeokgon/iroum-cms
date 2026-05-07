package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.SurveyCreateRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyDetail;
import kr.co.ircp.cms.domain.board.dto.SurveyQuestionDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 설문조사(Survey) 서비스 구현체.
 * REQ-BOARD-013: 설문 CRUD + 응답 제출 + 결과 통계
 *
 * // @MX:ANCHOR: [AUTO] SurveyServiceImpl — 설문 도메인 핵심 진입점
 * // @MX:REASON: SurveyController 외 결과 통계, 응답 제출 등 fan_in >= 3
 * // @MX:SPEC: REQ-BOARD-013
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyServiceImpl implements SurveyService {

    /** RATING 분포 라벨 (1~5). */
    private static final int RATING_MIN = 1;
    private static final int RATING_MAX = 5;

    private final SurveyMapper surveyMapper;
    private final SurveyQuestionMapper surveyQuestionMapper;
    private final SurveyResponseMapper surveyResponseMapper;
    private final SurveyAnswerMapper surveyAnswerMapper;

    @Override
    public PageResponse<SurveySummary> listSurveys(String status, String keyword, int page, int size) {
        int offset = page * size;
        List<Survey> rows = surveyMapper.findWithFilters(status, keyword, offset, size);
        long total = surveyMapper.countWithFilters(status, keyword);
        List<SurveySummary> content = rows.stream().map(this::toSummary).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public SurveyDetail getSurvey(Long id) {
        Survey survey = surveyMapper.findById(id)
                .orElseThrow(() -> new SurveyNotFoundException(id));
        List<SurveyQuestion> questions = surveyQuestionMapper.findBySurveyId(id);
        return toDetail(survey, questions);
    }

    @Override
    @Transactional
    public SurveyDetail createSurvey(SurveyCreateRequest req, Long createdBy) {
        // 1) 설문 마스터 INSERT (id 자동 채번)
        Survey survey = Survey.builder()
                .title(req.title())
                .descriptionHtml(req.descriptionHtml())
                .descriptionText(req.descriptionText() != null ? req.descriptionText() : stripHtml(req.descriptionHtml()))
                .startAt(req.startAt())
                .endAt(req.endAt())
                .isAnonymous(req.isAnonymous())
                .maxResponses(req.maxResponses())
                .status("DRAFT")
                .createdBy(createdBy)
                .build();
        surveyMapper.insert(survey);

        // 2) 질문 일괄 INSERT
        if (req.questions() != null && !req.questions().isEmpty()) {
            surveyQuestionMapper.insertBatch(survey.getId(), req.questions());
        }

        // 3) 결과 재조회
        return getSurvey(survey.getId());
    }

    @Override
    @Transactional
    public SurveyDetail updateSurvey(Long id, SurveyUpdateRequest req) {
        surveyMapper.findById(id)
                .orElseThrow(() -> new SurveyNotFoundException(id));

        // 1) 설문 마스터 UPDATE (부분 갱신)
        surveyMapper.update(id, req);

        // 2) 질문이 포함된 경우 기존 질문을 모두 삭제하고 새로 INSERT
        if (req.questions() != null) {
            surveyQuestionMapper.deleteBySurveyId(id);
            if (!req.questions().isEmpty()) {
                surveyQuestionMapper.insertBatch(id, req.questions());
            }
        }

        return getSurvey(id);
    }

    @Override
    @Transactional
    public void deleteSurvey(Long id) {
        surveyMapper.findById(id)
                .orElseThrow(() -> new SurveyNotFoundException(id));
        surveyMapper.softDelete(id);
    }

    /**
     * 설문 응답 제출.
     *
     * // @MX:NOTE: [AUTO] submitResponse — 설문 기간 검증, 중복 응답 차단, 익명 설문 respondent_id NULL 강제 처리
     * // @MX:SPEC: REQ-BOARD-013-D-3
     */
    @Override
    @Transactional
    public void submitResponse(Long surveyId, SurveySubmitRequest req, Long respondentId, String ipHash) {
        Survey survey = surveyMapper.findById(surveyId)
                .orElseThrow(() -> new SurveyNotFoundException(surveyId));

        // 1) 상태 / 기간 / 한도 검증
        if (!"OPEN".equals(survey.getStatus())) {
            throw new SurveyPeriodInvalidException();
        }
        Instant now = Instant.now();
        if (now.isBefore(survey.getStartAt()) || !now.isBefore(survey.getEndAt())) {
            throw new SurveyPeriodInvalidException();
        }
        if (survey.getMaxResponses() != null && survey.getResponseCount() >= survey.getMaxResponses()) {
            throw new SurveyPeriodInvalidException("응답 한도 초과");
        }

        // 2) 익명 설문이면 respondent_id 강제 NULL 처리
        Long effectiveRespondent = survey.isAnonymous() ? null : respondentId;

        // 3) 비익명 설문에 한해 동일 사용자 중복 응답 차단
        if (effectiveRespondent != null) {
            surveyResponseMapper.findByUserAndSurvey(surveyId, effectiveRespondent)
                    .ifPresent(r -> {
                        throw new SurveyPeriodInvalidException("이미 응답하셨습니다");
                    });
        }

        // 4) 응답 헤더 INSERT
        SurveyResponse response = SurveyResponse.builder()
                .surveyId(surveyId)
                .respondentId(effectiveRespondent)
                .respondentIpHash(ipHash != null ? ipHash : "")
                .startedAt(now)
                .build();
        surveyResponseMapper.insert(response);

        // 5) 답변 일괄 INSERT
        if (req.answers() != null && !req.answers().isEmpty()) {
            surveyAnswerMapper.insertBatch(response.getId(), req.answers());
        }

        // 6) 제출 완료 처리 + 응답 카운트 증가
        surveyResponseMapper.markSubmitted(response.getId());
        surveyMapper.incrementResponseCount(surveyId);
    }

    /**
     * 설문 결과 통계 집계.
     *
     * // @MX:NOTE: [AUTO] getResults — 질문 유형별 분기 집계 로직. 원시 쿼리 결과를 Java 단에서 가공
     * // @MX:SPEC: REQ-BOARD-013-D-5
     */
    @Override
    public SurveyResultDto getResults(Long surveyId) {
        Survey survey = surveyMapper.findById(surveyId)
                .orElseThrow(() -> new SurveyNotFoundException(surveyId));

        List<Map<String, Object>> rows = surveyAnswerMapper.aggregateByQuestion(surveyId);

        // 1) question_id 기준 그룹핑 (LinkedHashMap 으로 sort_order 유지)
        Map<Long, List<Map<String, Object>>> byQuestion = new LinkedHashMap<>();
        Map<Long, String> questionTextMap = new LinkedHashMap<>();
        Map<Long, String> questionTypeMap = new LinkedHashMap<>();
        int totalResponses = 0;
        for (Map<String, Object> row : rows) {
            Long questionId = toLong(row.get("question_id"));
            byQuestion.computeIfAbsent(questionId, k -> new ArrayList<>()).add(row);
            questionTextMap.putIfAbsent(questionId, (String) row.get("question_text"));
            questionTypeMap.putIfAbsent(questionId, (String) row.get("question_type"));
            // total_responses 는 모든 row 가 동일 (스칼라 서브쿼리)
            totalResponses = (int) toLong(row.get("total_responses"));
        }

        // 2) 질문 유형별 분포 가공
        List<SurveyResultDto.QuestionResult> questionResults = new ArrayList<>(byQuestion.size());
        for (Map.Entry<Long, List<Map<String, Object>>> entry : byQuestion.entrySet()) {
            Long questionId = entry.getKey();
            String questionType = questionTypeMap.get(questionId);
            List<Map<String, Object>> answerRows = entry.getValue();

            int totalAnswers = answerRows.stream()
                    .mapToInt(r -> (int) toLong(r.get("answer_count")))
                    .sum();
            List<SurveyResultDto.DistributionItem> distribution =
                    buildDistribution(questionType, answerRows, totalAnswers);

            questionResults.add(new SurveyResultDto.QuestionResult(
                    questionId,
                    questionTextMap.get(questionId),
                    questionType,
                    totalAnswers,
                    distribution
            ));
        }

        return new SurveyResultDto(
                survey.getId(),
                survey.getTitle(),
                totalResponses,
                questionResults
        );
    }

    // ─── 분포 가공 헬퍼 ───────────────────────────────────────────────────

    /**
     * 질문 유형별 응답 분포 가공.
     *
     * <ul>
     *   <li>SINGLE/MULTI: answer_options JSON 배열을 펼쳐 옵션 라벨별 카운트</li>
     *   <li>RATING: answer_rating 1~5 별 카운트</li>
     *   <li>DATE: answer_date 별 카운트</li>
     *   <li>TEXT: 단일 행 "응답" 라벨 + 카운트</li>
     * </ul>
     */
    private List<SurveyResultDto.DistributionItem> buildDistribution(
            String questionType,
            List<Map<String, Object>> answerRows,
            int totalAnswers) {

        if ("TEXT".equals(questionType)) {
            return List.of(new SurveyResultDto.DistributionItem(
                    "응답",
                    totalAnswers,
                    100.0
            ));
        }

        Map<String, Long> labelCounts = new LinkedHashMap<>();

        if ("SINGLE".equals(questionType) || "MULTI".equals(questionType)) {
            // answer_options 는 JSON 배열 문자열. 단순 문자열 매칭으로 라벨 카운트.
            for (Map<String, Object> row : answerRows) {
                String options = (String) row.get("answer_options");
                long count = toLong(row.get("answer_count"));
                if (options == null || count == 0L) {
                    continue;
                }
                // 대표 라벨로 JSON 배열 자체를 사용 (옵션 매칭은 프론트에서 옵션 사전과 join).
                labelCounts.merge(options, count, Long::sum);
            }
        } else if ("RATING".equals(questionType)) {
            // 1~5 점수별 카운트. 누락된 점수는 0 으로 표기.
            Map<Integer, Long> ratingCounts = new LinkedHashMap<>();
            for (int i = RATING_MIN; i <= RATING_MAX; i++) {
                ratingCounts.put(i, 0L);
            }
            for (Map<String, Object> row : answerRows) {
                Object rating = row.get("answer_rating");
                long count = toLong(row.get("answer_count"));
                if (rating == null || count == 0L) {
                    continue;
                }
                int r = ((Number) rating).intValue();
                ratingCounts.merge(r, count, Long::sum);
            }
            for (Map.Entry<Integer, Long> e : ratingCounts.entrySet()) {
                labelCounts.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else if ("DATE".equals(questionType)) {
            for (Map<String, Object> row : answerRows) {
                Object date = row.get("answer_date");
                long count = toLong(row.get("answer_count"));
                if (date == null || count == 0L) {
                    continue;
                }
                labelCounts.merge(date.toString(), count, Long::sum);
            }
        }

        List<SurveyResultDto.DistributionItem> result = new ArrayList<>(labelCounts.size());
        for (Map.Entry<String, Long> entry : labelCounts.entrySet()) {
            long count = entry.getValue();
            double percentage = totalAnswers == 0 ? 0.0 : ((double) count / totalAnswers) * 100.0;
            result.add(new SurveyResultDto.DistributionItem(entry.getKey(), count, percentage));
        }
        return result;
    }

    // ─── 변환 헬퍼 ─────────────────────────────────────────────────────────

    private SurveySummary toSummary(Survey s) {
        return new SurveySummary(
                s.getId(),
                s.getTitle(),
                s.getStatus(),
                s.isAnonymous(),
                s.getMaxResponses(),
                s.getResponseCount(),
                s.getStartAt(),
                s.getEndAt(),
                s.getCreatedAt()
        );
    }

    private SurveyDetail toDetail(Survey s, List<SurveyQuestion> questions) {
        List<SurveyQuestionDto> questionDtos = questions.stream()
                .map(q -> new SurveyQuestionDto(
                        q.getId(),
                        q.getSurveyId(),
                        q.getQuestionText(),
                        q.getQuestionType(),
                        q.isRequired(),
                        q.getSortOrder(),
                        q.getOptions()
                ))
                .toList();
        return new SurveyDetail(
                s.getId(),
                s.getTitle(),
                s.getDescriptionHtml(),
                s.getStatus(),
                s.isAnonymous(),
                s.getMaxResponses(),
                s.getResponseCount(),
                s.getStartAt(),
                s.getEndAt(),
                s.getCreatedAt(),
                questionDtos
        );
    }

    /** Object → long 안전 변환 (MyBatis Map 결과의 BigInteger/Long/Integer 모두 처리). */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }

    /** HTML 태그 제거 (간이). */
    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "").trim();
    }
}
