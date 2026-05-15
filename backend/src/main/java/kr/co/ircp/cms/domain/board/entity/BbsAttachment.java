package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 첨부파일 엔티티.
 * REQ-BOARD-004-D: 업로드, REQ-BOARD-005-D: 보안 다운로드
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsAttachment {

    private Long id;
    private Long postId;
    private Long commentId;
    private String fileName;
    private String storedPath;
    private String mimeType;
    private long sizeBytes;
    private String checksumSha256;
    private String scanStatus;
    private Instant scanCompletedAt;
    private long downloadCount;
    private Long uploadedBy;
    private Instant uploadedAt;
    private Instant deletedAt;
}
