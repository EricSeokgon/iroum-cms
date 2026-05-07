package kr.co.ircp.cms.domain.board.dto;

import java.util.List;

/**
 * 설문 결과 통계 DTO.
 * REQ-BOARD-013-D-5: GET /api/v1/surveys/{id}/results — 질문별 응답 분포.
 */
public record SurveyResultDto(
        Long surveyId,
        String title,
        int totalResponses,
        List<QuestionResult> questions
) {

    /**
     * 질문 1개에 대한 결과 집계.
     */
    public record QuestionResult(
            Long questionId,
            String questionText,
            String questionType,
            int totalAnswers,
            List<DistributionItem> distribution
    ) {
    }

    /**
     * 분포 항목 (옵션 라벨 / RATING 점수 / "응답") 별 응답 수와 비율.
     */
    public record DistributionItem(
            String label,
            long count,
            double percentage
    ) {
    }
}
