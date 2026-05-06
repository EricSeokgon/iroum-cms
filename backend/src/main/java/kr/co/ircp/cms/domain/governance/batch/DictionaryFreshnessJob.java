package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataDictionaryHistory;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.repository.DataDictionaryMapper;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 데이터 표준 사전 현행화 검증 Job.
 *
 * <p>SPEC-CMS-009 REQ-GOV-005 — 매일 06:30.
 * information_schema.columns ↔ data_dictionary 차이 비교.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictionaryFreshnessJob {

    static final String JOB_NAME = "DictionaryFreshnessJob";

    private final DataDictionaryMapper dictionaryMapper;
    private final DataQualityMapper qualityMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        List<DataDictionary> registered = dictionaryMapper.findAll();
        List<DataDictionaryMapper.SchemaColumn> actual = dictionaryMapper.findActualSchemaColumns();

        Set<String> registeredKeys = new HashSet<>();
        Set<String> actualKeys = new HashSet<>();
        for (DataDictionary d : registered) {
            if (!"REMOVED".equals(d.getStatus())) {
                registeredKeys.add(d.getTableName() + "." + d.getColumnName());
            }
        }
        for (DataDictionaryMapper.SchemaColumn c : actual) {
            actualKeys.add(c.getTableName() + "." + c.getColumnName());
        }

        // FRESHNESS 룰을 갖는 data_dictionary가 없으면 룰을 동적 생성하지 않고, 직접 report 기록.
        // 본 Job은 별도 룰 ID 없이 placeholder rule_id 사용 — Step 1: rule_id를 NULL 가능하게 변경할 수도 있으나
        // FK 제약 때문에 실재 룰을 사용해야 한다. 'data_dictionary' FRESHNESS 룰을 시드로 보장.
        DataQualityRule freshnessRule = qualityMapper.findActiveRules().stream()
                .filter(r -> "data_dictionary".equals(r.getTargetTable()) && "FRESHNESS".equals(r.getRuleType()))
                .findFirst()
                .orElse(null);
        if (freshnessRule == null) {
            log.warn("data_dictionary FRESHNESS 룰이 없음 — DictionaryFreshnessJob의 missing/stale report 미적재");
            return 0;
        }

        int reported = 0;
        for (String key : actualKeys) {
            if (!registeredKeys.contains(key)) {
                qualityMapper.insertReport(DataQualityReport.builder()
                        .ruleId(freshnessRule.getId())
                        .checkedAt(Instant.now())
                        .measuredValue(BigDecimal.ZERO)
                        .violation(true)
                        .detail("MISSING_IN_DICTIONARY: " + key)
                        .notified(false)
                        .build());
                reported++;
            }
        }
        for (String key : registeredKeys) {
            if (!actualKeys.contains(key)) {
                qualityMapper.insertReport(DataQualityReport.builder()
                        .ruleId(freshnessRule.getId())
                        .checkedAt(Instant.now())
                        .measuredValue(BigDecimal.ZERO)
                        .violation(true)
                        .detail("STALE_IN_DICTIONARY: " + key)
                        .notified(false)
                        .build());
                reported++;
            }
        }
        return reported;
    }

    @Scheduled(cron = "${governance.batch.dictionary-freshness.cron:0 30 6 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "QUALITY", this::run);
    }

    // history 사용 안 함 — import 정리용 dummy
    @SuppressWarnings("unused")
    private void unused(DataDictionaryHistory h) {
    }
}
