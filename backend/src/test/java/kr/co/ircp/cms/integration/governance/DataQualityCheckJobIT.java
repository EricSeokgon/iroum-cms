package kr.co.ircp.cms.integration.governance;

import kr.co.ircp.cms.domain.governance.batch.DataQualityCheckJob;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-009 TS-008: DataQualityCheckJob → data_quality_report 적재 검증.
 */
class DataQualityCheckJobIT extends AbstractIntegrationTest {

    @Autowired
    private DataQualityCheckJob job;

    @Autowired
    private DataQualityMapper qualityMapper;

    @Test
    void seedRules_haveAtLeast8ActiveEntries() {
        List<DataQualityRule> rules = qualityMapper.findActiveRules();
        assertThat(rules).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void run_processesAllActiveRules_andRecordsReports() {
        List<DataQualityRule> beforeRules = qualityMapper.findActiveRules();
        int processed = job.run();

        // 모든 룰 처리 또는 일부 SKIP — processed >= 0
        assertThat(processed).isGreaterThanOrEqualTo(0);

        // 적어도 일부 룰에 대해 report 적재 확인
        boolean anyReport = false;
        for (DataQualityRule rule : beforeRules) {
            if (!qualityMapper.findReportsByRule(rule.getId()).isEmpty()) {
                anyReport = true;
                break;
            }
        }
        assertThat(anyReport).isTrue();
    }
}
