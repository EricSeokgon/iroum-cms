package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.batch.BoardStatsDailyJob;
import kr.co.ircp.cms.domain.governance.batch.BoardStatsMonthlyJob;
import kr.co.ircp.cms.domain.governance.batch.ContentViewStatsDailyJob;
import kr.co.ircp.cms.domain.governance.batch.ContentViewStatsMonthlyJob;
import kr.co.ircp.cms.domain.governance.batch.PolicyMatchStatsJob;
import kr.co.ircp.cms.domain.governance.batch.SafetyStatsMonthlyJob;
import kr.co.ircp.cms.domain.governance.entity.BoardStatsDaily;
import kr.co.ircp.cms.domain.governance.entity.BoardStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.ContentViewStatsDaily;
import kr.co.ircp.cms.domain.governance.entity.ContentViewStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.PolicyMatchStatsMonthly;
import kr.co.ircp.cms.domain.governance.entity.SafetyStatsMonthly;
import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 거버넌스 통계 조회·재계산 서비스.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001~004 — board/content/policy/safety stats 조회 + 수동 재계산.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GovernanceStatsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final GovernanceStatsMapper mapper;
    private final BoardStatsDailyJob       boardDailyJob;
    private final BoardStatsMonthlyJob     boardMonthlyJob;
    private final ContentViewStatsDailyJob   contentDailyJob;
    private final ContentViewStatsMonthlyJob contentMonthlyJob;
    private final PolicyMatchStatsJob      policyJob;
    private final SafetyStatsMonthlyJob    safetyJob;

    public List<BoardStatsDaily> findBoardDaily(Long boardId, LocalDate from, LocalDate to) {
        return mapper.findBoardStatsDailyRange(buildDateParams("boardId", boardId, from, to));
    }

    public List<BoardStatsMonthly> findBoardMonthly(Long boardId, LocalDate from, LocalDate to) {
        return mapper.findBoardStatsMonthlyRange(buildMonthParams("boardId", boardId, from, to));
    }

    public List<ContentViewStatsDaily> findContentDaily(Long contentId, LocalDate from, LocalDate to) {
        return mapper.findContentViewStatsDailyRange(buildDateParams("contentId", contentId, from, to));
    }

    public List<ContentViewStatsMonthly> findContentMonthly(Long contentId, LocalDate from, LocalDate to) {
        return mapper.findContentViewStatsMonthlyRange(buildMonthParams("contentId", contentId, from, to));
    }

    public List<PolicyMatchStatsMonthly> findPolicyStats(Long policyId, LocalDate from, LocalDate to) {
        return mapper.findPolicyMatchStatsRange(buildMonthParams("policyId", policyId, from, to));
    }

    public List<SafetyStatsMonthly> findSafetyStats(String category, LocalDate from, LocalDate to) {
        Map<String, Object> p = new HashMap<>();
        p.put("category", category);
        p.put("from", from == null ? null : from.format(MONTH_FMT));
        p.put("to",   to   == null ? null : to.format(MONTH_FMT));
        return mapper.findSafetyStatsRange(p);
    }

    /** 수동 재계산 — job 이름과 dateRange로 dispatch. 처리된 job 식별자 반환. */
    @Transactional
    public Map<String, Object> recompute(String job, LocalDate from, LocalDate to) {
        log.info("Manual stats recompute job={} from={} to={}", job, from, to);
        LocalDate effectiveFrom = from == null ? LocalDate.now().minusDays(1) : from;
        int processed = switch (job) {
            case "BoardStatsDailyJob"        -> boardDailyJob.run(effectiveFrom);
            case "BoardStatsMonthlyJob"      -> boardMonthlyJob.run(monthOf(effectiveFrom));
            case "ContentViewStatsDailyJob"   -> contentDailyJob.run(effectiveFrom);
            case "ContentViewStatsMonthlyJob" -> contentMonthlyJob.run(monthOf(effectiveFrom));
            case "PolicyMatchStatsJob"       -> policyJob.run(monthOf(effectiveFrom));
            case "SafetyStatsMonthlyJob"     -> safetyJob.run(monthOf(effectiveFrom));
            default -> throw new IllegalArgumentException("Unknown stats job: " + job);
        };
        return Map.of("job", job, "processed", processed);
    }

    private Map<String, Object> buildDateParams(String idKey, Long id, LocalDate from, LocalDate to) {
        Map<String, Object> p = new HashMap<>();
        p.put(idKey, id);
        p.put("from", from);
        p.put("to",   to);
        return p;
    }

    private Map<String, Object> buildMonthParams(String idKey, Long id, LocalDate from, LocalDate to) {
        Map<String, Object> p = new HashMap<>();
        p.put(idKey, id);
        p.put("from", from == null ? null : from.format(MONTH_FMT));
        p.put("to",   to   == null ? null : to.format(MONTH_FMT));
        return p;
    }

    private String monthOf(LocalDate d) {
        if (d == null) return LocalDate.now().format(MONTH_FMT);
        return d.format(MONTH_FMT);
    }
}
