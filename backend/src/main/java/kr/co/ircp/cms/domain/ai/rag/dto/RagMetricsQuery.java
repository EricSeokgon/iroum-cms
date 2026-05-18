package kr.co.ircp.cms.domain.ai.rag.dto;

import java.time.LocalDate;

/**
 * RAG 메트릭 조회 기간 파라미터 (MyBatis 파라미터).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015 — from/to NULL이면 매퍼에서 기본 윈도우 적용.
 *
 * @param from 집계 시작일 (NULL=7일 전)
 * @param to   집계 종료일 (NULL=오늘)
 */
public record RagMetricsQuery(LocalDate from, LocalDate to) {
}
