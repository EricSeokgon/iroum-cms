package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Q&A 엔티티.
 * REQ-BOARD-008-D: 질문/답변 워크플로
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Qna {

    private Long id;
    private String title;
    private String questionHtml;
    private String questionText;
    private Long questionerId;
    private Long answererId;
    private String answerHtml;
    private String answerText;
    private Instant answeredAt;
    private boolean isPrivate;
    private String status;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    // SPEC-CMS-AI-004: AI 스마트 태그 (V54 qna.tags TEXT[] NOT NULL DEFAULT '{}').
    // @Builder.Default 로 null 방지 — StringArrayTypeHandler.createArrayOf 는 null 입력 시 NPE 발생.
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
