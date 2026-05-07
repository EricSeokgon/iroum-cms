package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.repository.PublicationZipArchiveMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PublicationZipExpireJob GREEN 단계 테스트.
 * REQ-BOARD-012-D-4: 매일 자정 만료된 ZIP 아카이브 소프트 삭제 배치
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PublicationZipExpireJob GREEN 테스트 (REQ-BOARD-012-D-4)")
class PublicationZipExpireJobTest {

    @Mock
    private PublicationZipArchiveMapper archiveMapper;

    private PublicationZipExpireJob job;

    @BeforeEach
    void setUp() {
        job = new PublicationZipExpireJob(archiveMapper);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-012-D-4: 만료 ZIP 소프트 삭제 배치
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("만료 처리 — archiveMapper.softDeleteExpired() 1회 호출")
    void expireZipArchives_invokesMapperSoftDeleteExpired() {
        // act
        job.expireZipArchives();

        // assert
        verify(archiveMapper, times(1)).softDeleteExpired();
    }

    @Test
    @DisplayName("만료 처리 — 매퍼가 정상 반환할 때 예외 발생 없이 완료")
    void expireZipArchives_callsCompleteWithoutException() {
        // act + assert — 매퍼가 정상 동작하면 어떠한 예외도 외부로 전파되지 않음
        assertThatCode(() -> job.expireZipArchives()).doesNotThrowAnyException();

        verify(archiveMapper).softDeleteExpired();
    }
}
