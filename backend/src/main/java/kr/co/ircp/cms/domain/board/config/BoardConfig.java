package kr.co.ircp.cms.domain.board.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 게시판 도메인 설정 클래스.
 * REQ-BOARD-004, REQ-BOARD-005: 첨부파일 설정 등록
 */
@Configuration
@EnableConfigurationProperties(BoardAttachmentProperties.class)
public class BoardConfig {
}
