package kr.co.ircp.cms.integration.governance;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.service.RetentionPolicyService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-009 TS-001: retention_policy 시드 데이터 검증.
 *
 * <p>5개 보존 정책(personal_data_access_log, audit_log, login_history, access_log, integration_log)이
 * V18 마이그레이션에 의해 자동 적재되어야 한다.
 */
class RetentionPolicySeedIT extends AbstractIntegrationTest {

    @Autowired
    private RetentionPolicyService policyService;

    @Test
    void fiveSeedPolicies_allActive() {
        List<RetentionPolicy> all = policyService.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(5);

        List<String> targetTables = all.stream().map(RetentionPolicy::getTargetTable).toList();
        assertThat(targetTables).contains(
                "personal_data_access_log",
                "audit_log",
                "login_history",
                "access_log",
                "integration_log"
        );

        // 모두 ACTIVE
        assertThat(all).allSatisfy(p ->
                assertThat(p.getStatus()).isEqualTo("ACTIVE"));
    }

    @Test
    void personalDataAccessLogPolicy_correctRetentionMonths() {
        RetentionPolicy policy = policyService.findByTargetTable("personal_data_access_log").orElseThrow();
        assertThat(policy.getRetentionMonths()).isEqualTo(6);
        assertThat(policy.getPolicyType()).isEqualTo("ARCHIVE");
        assertThat(policy.getArchiveTable()).isEqualTo("personal_data_access_log_archive");
    }

    @Test
    void auditLogPolicy_correctRetentionMonths() {
        RetentionPolicy policy = policyService.findByTargetTable("audit_log").orElseThrow();
        assertThat(policy.getRetentionMonths()).isEqualTo(60);
        assertThat(policy.getPolicyType()).isEqualTo("ARCHIVE");
    }

    @Test
    void loginHistoryPolicy_isDeleteType() {
        RetentionPolicy policy = policyService.findByTargetTable("login_history").orElseThrow();
        assertThat(policy.getRetentionMonths()).isEqualTo(12);
        assertThat(policy.getPolicyType()).isEqualTo("DELETE");
        assertThat(policy.getArchiveTable()).isNull();
    }
}
