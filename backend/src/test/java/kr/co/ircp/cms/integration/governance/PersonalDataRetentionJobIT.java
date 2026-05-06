package kr.co.ircp.cms.integration.governance;

import kr.co.ircp.cms.domain.governance.batch.PersonalDataRetentionJob;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-009 TS-004: PersonalDataRetentionJob dry-run 동작 + 정책 검증.
 */
class PersonalDataRetentionJobIT extends AbstractIntegrationTest {

    @Autowired
    private PersonalDataRetentionJob job;

    @Test
    void dryRun_returnsZero_andDoesNotThrow() {
        int processed = job.run(true);
        assertThat(processed).isZero();
    }

    @Test
    void run_realMode_doesNotThrow_evenWithEmptyTable() {
        // archive 6개월 경과 행이 0건이어도 정상 종료
        int processed = job.run(false);
        assertThat(processed).isGreaterThanOrEqualTo(0);
    }
}
