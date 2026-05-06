package kr.co.ircp.cms.integration.governance;

import kr.co.ircp.cms.domain.governance.batch.BoardStatsDailyJob;
import kr.co.ircp.cms.domain.governance.entity.BoardStatsDaily;
import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-009 TS-006/REQ-DATA-001: BoardStatsDailyJob page_url 정규식 매핑 + UPSERT 검증.
 */
@Transactional
class BoardStatsDailyJobIT extends AbstractIntegrationTest {

    @Autowired
    private BoardStatsDailyJob job;

    @Autowired
    private GovernanceStatsMapper statsMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void run_aggregatesAccessLogIntoBoardStatsDaily() {
        LocalDate testDate = LocalDate.of(2026, 4, 15);
        // access_log_y2026m04 파티션 사용 (V14 시드)
        // ip_hash는 64자 SHA-256 16진수 — 단순 더미값 사용
        String ipHash = "a".repeat(64);
        for (int i = 0; i < 5; i++) {
            jdbc.update(
                    "INSERT INTO access_log (site_id, ip_hash, page_url, status_code, response_time_ms, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamptz)",
                    1, ipHash, "/board/1/post/" + i, 200, 100,
                    testDate.atStartOfDay().plusHours(10).toString()
            );
        }
        // /contents/ pattern (board가 아니므로 제외되어야 함)
        jdbc.update(
                "INSERT INTO access_log (site_id, ip_hash, page_url, status_code, response_time_ms, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?::timestamptz)",
                1, ipHash, "/contents/100", 200, 100,
                testDate.atStartOfDay().plusHours(11).toString()
        );

        // when
        int upserted = job.run(testDate);

        // then
        assertThat(upserted).isGreaterThanOrEqualTo(1);
        List<BoardStatsDaily> stats = statsMapper.findBoardStatsDaily(testDate);
        assertThat(stats).isNotEmpty();
        BoardStatsDaily board1 = stats.stream()
                .filter(s -> s.getBoardId() == 1L)
                .findFirst()
                .orElseThrow();
        assertThat(board1.getTotalViews()).isEqualTo(5);
        assertThat(board1.getUniqueVisitors()).isEqualTo(1);  // 같은 ip_hash 5회
    }
}
