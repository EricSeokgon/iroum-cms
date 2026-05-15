package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 발간자료 ZIP 아카이브 엔티티.
 * REQ-BOARD-012-D-4: ZIP 다운로드 (동기 ≤50MB, 비동기 >50MB, 7일 보관)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationZipArchive {

    private Long id;
    private UUID downloadId;
    private Long requestedBy;
    private Long postId;
    /** PostgreSQL UUID[] ↔ List&lt;UUID&gt; (UuidArrayTypeHandler 사용). */
    private List<UUID> assetUuids;
    private String zipFilePath;
    private long sizeBytes;
    /** SYNC | ASYNC. */
    private String mode;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant deletedAt;
    private int downloadCount;
    private Instant lastDownloadedAt;
}
