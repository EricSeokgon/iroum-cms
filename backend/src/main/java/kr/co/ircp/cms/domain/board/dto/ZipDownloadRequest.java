package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * 발간자료 ZIP 다운로드 요청 DTO.
 * REQ-BOARD-012-D-4: 첨부파일 UUID 목록을 받아 ZIP으로 묶어 제공
 */
public record ZipDownloadRequest(
        @NotEmpty List<UUID> assetUuids
) {
}
