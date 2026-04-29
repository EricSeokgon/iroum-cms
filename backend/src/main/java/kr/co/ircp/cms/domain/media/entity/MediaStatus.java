package kr.co.ircp.cms.domain.media.entity;

/**
 * 미디어 자산 처리 상태.
 * PROCESSING → READY → ARCHIVED 또는 DELETED
 */
public enum MediaStatus {
    PROCESSING,
    READY,
    ARCHIVED,
    DELETED
}
