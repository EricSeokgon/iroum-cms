package kr.co.ircp.cms.domain.board.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 첨부파일 업로드·다운로드 설정.
 * REQ-BOARD-004: 업로드 제약 (크기, 타입, 경로)
 * REQ-BOARD-005: 보안 다운로드 (HMAC 키, TTL)
 *
 * // @MX:ANCHOR: [AUTO] BoardAttachmentProperties — 첨부파일 설정 중앙 관리
 * // @MX:REASON: AttachmentServiceImpl, AttachmentController에서 참조 (fan_in >= 3)
 * // @MX:WARN: [AUTO] hmacSecret은 환경변수 iroum.board.attachment.hmac-secret에서 주입
 * // @MX:REASON: 서명 키 노출 시 모든 첨부파일 무단 다운로드 가능. 절대 기본값 하드코딩 금지.
 */
@ConfigurationProperties(prefix = "iroum.board.attachment")
public record BoardAttachmentProperties(
        /** 첨부파일 저장 기본 경로 */
        String storagePath,
        /** 허용 MIME 타입 목록 */
        List<String> allowedMimeTypes,
        /** 전역 최대 파일 크기 (KB) */
        long globalMaxSizeKb,
        /** HMAC-SHA256 서명 키 (환경변수 주입 필수) */
        String hmacSecret,
        /** 다운로드 URL TTL (초, 기본 900 = 15분) */
        int downloadTtlSeconds
) {
}
