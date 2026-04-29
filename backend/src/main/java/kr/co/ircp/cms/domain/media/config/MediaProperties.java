package kr.co.ircp.cms.domain.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 미디어 라이브러리 설정.
 * REQ-MEDIA-001-D-5: MIME 화이트리스트
 * REQ-MEDIA-004-D: 서명 URL TTL 15분
 *
 * // @MX:ANCHOR: [AUTO] MediaProperties — 미디어 설정 중앙 관리
 * // @MX:REASON: MediaServiceImpl, LocalFileSystemStorage, MediaController에서 참조 (fan_in >= 3)
 */
@Data
@Component
@ConfigurationProperties(prefix = "iroum.media")
public class MediaProperties {

    /** 미디어 파일 저장 기본 경로 (webroot 외부) */
    private String basePath = "/var/iroum-cms/media";

    /** HMAC-SHA256 서명 키 (환경변수 주입 필수) */
    private String signedUrlSecret = "CHANGE_ME_IN_PRODUCTION";

    /** 서명 URL TTL (기본 15분) */
    private Duration signedUrlTtl = Duration.ofMinutes(15);

    /** 허용 MIME 타입 화이트리스트 (REQ-MEDIA-001-D-5) */
    private List<String> allowedMimeTypes = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/ogg", "audio/wav",
            "application/pdf",
            "text/plain"
    );

    /** 최대 파일 크기 (MB, 기본 100MB) */
    private int maxSizeMb = 100;

    public long getMaxSizeBytes() {
        return (long) maxSizeMb * 1024 * 1024;
    }
}
