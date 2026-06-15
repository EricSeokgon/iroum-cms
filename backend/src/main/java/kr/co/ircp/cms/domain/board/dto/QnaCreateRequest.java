package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Q&A 질문 작성 요청 DTO.
 * REQ-BOARD-008-C: Q&A 질문 등록
 */
public record QnaCreateRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String questionHtml,
        boolean isPrivate,
        // SPEC-CMS-AI-004: AI 스마트 태그 (선택, 공백 허용, 최대 5개). null/미전송 시 빈 목록 처리.
        List<String> tags
) {
}
