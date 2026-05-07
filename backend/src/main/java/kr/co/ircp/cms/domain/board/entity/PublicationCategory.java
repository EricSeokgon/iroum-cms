package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 발간자료 카테고리 엔티티 (계층형, 최대 depth 3).
 * REQ-BOARD-012-D: 발간자료 카테고리 관리
 */
@Data
@Builder
public class PublicationCategory {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private short depth;
    private int sortOrder;
    private String status;
    private Instant createdAt;

    /** 트리 조회용 (DB 컬럼 아님). */
    private List<PublicationCategory> children;
}
