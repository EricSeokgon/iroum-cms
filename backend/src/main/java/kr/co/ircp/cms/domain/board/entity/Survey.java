package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 설문조사 마스터 엔티티.
 * REQ-BOARD-013: 설문조사 메인 정보 (제목, 기간, 상태, 응답 한도)
 */
@Data
@Builder
public class Survey {

    private Long id;
    private Long bbsId;
    private String title;
    private String descriptionHtml;
    private String descriptionText;
    private Instant startAt;
    private Instant endAt;
    /** JSONB → String (애플리케이션 단에서 파싱). */
    private String targetRoleCodes;
    private boolean isAnonymous;
    private Integer maxResponses;
    private int responseCount;
    /** DRAFT / OPEN / CLOSED / HIDDEN */
    private String status;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
