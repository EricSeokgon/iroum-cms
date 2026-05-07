package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.repository.PublicationZipArchiveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발간자료 ZIP 아카이브 만료 배치 잡.
 *
 * // @MX:NOTE: [AUTO] 매일 자정 만료된 ZIP 아카이브 소프트 삭제 배치
 * // @MX:SPEC: REQ-BOARD-012-D-4
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PublicationZipExpireJob {

    private final PublicationZipArchiveMapper archiveMapper;

    /** 매일 자정 만료된 ZIP 아카이브를 소프트 삭제한다. */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireZipArchives() {
        log.info("ZIP 아카이브 만료 처리 시작");
        archiveMapper.softDeleteExpired();
        log.info("ZIP 아카이브 만료 처리 완료");
    }
}
