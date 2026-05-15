package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

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
}
