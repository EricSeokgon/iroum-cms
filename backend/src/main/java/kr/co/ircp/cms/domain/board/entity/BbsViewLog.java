package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 게시글 조회 이력 엔티티 (중복 방지 + 통계).
 * REQ-BOARD-002-D-3: view_log dedupe 후 view_count 증가
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsViewLog {

    private Long id;
    private Long postId;
    private Long userId;
    private String ipHash;
    private String userAgentHash;
    private Instant viewedAt;
}
