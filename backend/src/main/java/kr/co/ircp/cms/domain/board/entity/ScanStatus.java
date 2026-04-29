package kr.co.ircp.cms.domain.board.entity;

/**
 * 첨부파일 바이러스 스캔 상태 enum.
 * REQ-BOARD-004-D-5, REQ-BOARD-005-D-5
 */
public enum ScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    SCAN_FAILED,
    SKIPPED
}
