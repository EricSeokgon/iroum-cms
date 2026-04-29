package kr.co.ircp.cms.domain.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 미디어 도메인 설정 활성화.
 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfig {
}
