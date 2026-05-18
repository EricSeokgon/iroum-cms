package kr.co.ircp.cms.domain.ai.rag.dto;

import java.time.LocalDate;

/**
 * RAG 일자별 시계열 원시 행 (MyBatis resultType).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015 — 일자별 질의 수·HELPFUL·피드백 수.
 *
 * @param day           집계 일자
 * @param queryCount    해당 일자 질의 수
 * @param helpfulCount  해당 일자 HELPFUL 피드백 수
 * @param feedbackCount 해당 일자 피드백 총 수
 */
public record RagTimeSeriesRow(
        LocalDate day,
        long queryCount,
        long helpfulCount,
        long feedbackCount) {
}
