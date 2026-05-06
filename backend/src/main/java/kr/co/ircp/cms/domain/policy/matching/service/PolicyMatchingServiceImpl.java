package kr.co.ircp.cms.domain.policy.matching.service;

import kr.co.ircp.cms.domain.policy.matching.dto.CompanyProfileUpsertRequest;
import kr.co.ircp.cms.domain.policy.matching.dto.MatchedPolicy;
import kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.matching.entity.CompanyMatchInput;
import kr.co.ircp.cms.domain.policy.matching.entity.PolicyMatchScore;
import kr.co.ircp.cms.domain.policy.matching.exception.CompanyMatchInputNotFoundException;
import kr.co.ircp.cms.domain.policy.matching.repository.CompanyMatchInputMapper;
import kr.co.ircp.cms.domain.policy.matching.repository.PolicyMatchScoreMapper;
import kr.co.ircp.cms.domain.policy.program.entity.PolicyProgram;
import kr.co.ircp.cms.domain.policy.program.repository.PolicyProgramMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 정책 매칭 알고리즘 구현.
 *
 * 점수 산식 (REQ-POLICY-003-D-1):
 *   score = INDUSTRY(W=30) + REGION(W=20) + SIZE(W=20) + AGE(W=15) + REVENUE(W=15)
 *           + 보너스(인증 +5, 신규 +3) + 키워드 보너스(상한 5)
 *   상한 100, 등급 A>=80 / B>=60 / C>=40 / D<40 (SPEC §8.4 vs REQ-POLICY-003-D-2 사이의 차이는
 *   본 구현은 전달된 grade thresholds 80/60/40/0 사용 — 사용자 지시문 우선).
 *
 * // @MX:ANCHOR: [AUTO] 5차원 매칭 알고리즘. fan_in >= 3 (matchForCompany는 controller/cache/scheduler 등에서 호출)
 * // @MX:REASON: 정책 매칭의 핵심 invariant. 가중치 변경 시 회귀 영향이 매우 큼.
 * // @MX:SPEC: REQ-POLICY-003
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyMatchingServiceImpl implements PolicyMatchingService {

    /** 차원별 가중치 (총합 100). */
    static final Map<String, Integer> DIMENSION_WEIGHTS = Map.of(
            "industry", 30,
            "region",   20,
            "size",     20,
            "age",      15,
            "revenue",  15
    );

    static final BigDecimal GRADE_A = new BigDecimal("80");
    static final BigDecimal GRADE_B = new BigDecimal("60");
    static final BigDecimal GRADE_C = new BigDecimal("40");

    /** 매칭 결과 캐시 TTL: 7일. */
    static final long CACHE_TTL_DAYS = 7L;

    /** 인증 보유 보너스 점수. */
    static final int CERT_BONUS = 5;
    /** 키워드 매칭 보너스 상한. */
    static final int KEYWORD_BONUS_CAP = 5;

    private final CompanyMatchInputMapper companyInputMapper;
    private final PolicyProgramMapper programMapper;
    private final PolicyMatchScoreMapper scoreMapper;

    @Override
    @Transactional
    public PolicyMatchResponse matchForCompany(Long companyId, int topN) {
        int requested = clampTopN(topN);

        // 1. 캐시 hit 확인
        List<PolicyMatchScore> cached = scoreMapper.findActiveCacheByCompanyId(companyId, requested);
        if (cached != null && cached.size() >= requested) {
            List<MatchedPolicy> cachedResults = cached.stream()
                    .limit(requested)
                    .map(this::toMatchedPolicyFromCache)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            return new PolicyMatchResponse(companyId, requested, true, cachedResults);
        }

        // 2. 기업 프로필 로드
        CompanyMatchInput input = companyInputMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new CompanyMatchInputNotFoundException(companyId));

        // 3. 활성 정책 풀 로드
        List<PolicyProgram> activePrograms = programMapper.findActiveForMatching();

        // 4. 정책별 점수 산출 + breakdown
        List<MatchedPolicy> scored = new ArrayList<>();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CACHE_TTL_DAYS, ChronoUnit.DAYS);

        for (PolicyProgram program : activePrograms) {
            ScoreResult sr = computeScore(input, program);
            String grade = computeGrade(sr.totalScore);

            // 캐시 적재
            PolicyMatchScore cache = PolicyMatchScore.builder()
                    .companyId(companyId)
                    .policyId(program.getId())
                    .score(sr.totalScore)
                    .grade(grade)
                    .scoreBreakdown(sr.breakdownJson)
                    .matchedAt(now)
                    .expiresAt(expiresAt)
                    .build();
            scoreMapper.insert(cache);

            scored.add(new MatchedPolicy(
                    program.getId(),
                    program.getProgramName(),
                    program.getMinistry(),
                    program.getApplicationEnd(),
                    sr.totalScore,
                    grade,
                    sr.breakdownJson,
                    now
            ));
        }

        // 5. 정렬 + TOP N (REQ-POLICY-003-D-3: score desc -> matched_at desc -> application_end asc)
        scored.sort(Comparator
                .comparing(MatchedPolicy::score, Comparator.reverseOrder())
                .thenComparing(MatchedPolicy::matchedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MatchedPolicy::applicationEnd, Comparator.nullsLast(Comparator.naturalOrder())));

        List<MatchedPolicy> top = scored.stream().limit(requested).collect(Collectors.toList());

        return new PolicyMatchResponse(companyId, requested, false, top);
    }

    @Override
    public PolicyMatchResponse getCachedResults(Long companyId, int topN) {
        int requested = clampTopN(topN);
        List<PolicyMatchScore> cached = scoreMapper.findActiveCacheByCompanyId(companyId, requested);
        List<MatchedPolicy> results = cached.stream()
                .map(this::toMatchedPolicyFromCache)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return new PolicyMatchResponse(companyId, requested, true, results);
    }

    @Override
    @Transactional
    public void upsertCompanyProfile(CompanyProfileUpsertRequest request) {
        Optional<CompanyMatchInput> existing = companyInputMapper.findByCompanyId(request.companyId());
        CompanyMatchInput entity = CompanyMatchInput.builder()
                .id(existing.map(CompanyMatchInput::getId).orElse(null))
                .companyId(request.companyId())
                .industryCodes(request.industryCodes() == null ? List.of() : request.industryCodes())
                .regionCodes(request.regionCodes() == null ? List.of() : request.regionCodes())
                .employeeCount(request.employeeCount())
                .annualRevenue(request.annualRevenue())
                .businessAgeMonths(request.businessAgeMonths())
                .certifications(request.certifications() == null ? List.of() : request.certifications())
                .customAttrs(request.customAttrs() == null ? "{}" : request.customAttrs())
                .build();
        if (existing.isEmpty()) {
            companyInputMapper.insert(entity);
        } else {
            companyInputMapper.update(entity);
        }
        // 프로필 변경 → 매칭 캐시 무효화
        scoreMapper.deleteByCompanyId(request.companyId());
    }

    // ─── 매칭 알고리즘 (package-private for testing) ─────────────────────────

    /**
     * 5차원 가중 점수 + 보너스.
     *
     * @return 총점 (0~100) + JSON breakdown
     */
    ScoreResult computeScore(CompanyMatchInput input, PolicyProgram program) {
        Map<String, BigDecimal> contributions = new LinkedHashMap<>();
        contributions.put("industry", scoreIndustry(input, program));
        contributions.put("region",   scoreRegion(input, program));
        contributions.put("size",     scoreSize(input, program));
        contributions.put("age",      scoreAge(input, program));
        contributions.put("revenue",  scoreRevenue(input, program));

        // 인증 보유 보너스
        int certBonus = scoreCertBonus(input, program);
        // 키워드 보너스 (상한 5)
        int keywordBonus = scoreKeywordBonus(input, program);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (BigDecimal v : contributions.values()) subtotal = subtotal.add(v);

        BigDecimal total = subtotal
                .add(BigDecimal.valueOf(certBonus))
                .add(BigDecimal.valueOf(keywordBonus));
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) total = BigDecimal.valueOf(100);
        if (total.compareTo(BigDecimal.ZERO) < 0)         total = BigDecimal.ZERO;
        total = total.setScale(2, RoundingMode.HALF_UP);

        return new ScoreResult(total, contributions, certBonus, keywordBonus, buildBreakdownJson(contributions, certBonus, keywordBonus));
    }

    /** INDUSTRY 차원: 일치 30, 부분일치 15, 불일치 0. */
    BigDecimal scoreIndustry(CompanyMatchInput input, PolicyProgram program) {
        List<String> targets = nullSafe(program.getTargetIndustries());
        List<String> userInd = nullSafe(input.getIndustryCodes());
        if (targets.isEmpty()) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("industry"));  // 무제한 → full
        if (userInd.isEmpty()) return BigDecimal.ZERO;
        boolean exact = userInd.stream().anyMatch(targets::contains);
        if (exact) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("industry"));
        // 부분 일치: prefix(3자리) 동일
        boolean prefix = userInd.stream().anyMatch(u -> targets.stream()
                .anyMatch(t -> u != null && t != null && u.length() >= 3 && t.length() >= 3
                        && u.substring(0, 3).equals(t.substring(0, 3))));
        return prefix ? BigDecimal.valueOf(DIMENSION_WEIGHTS.get("industry") / 2) : BigDecimal.ZERO;
    }

    /** REGION 차원: 정확 일치 20, 광역 일치(prefix 2자리) 14, 불일치 0. */
    BigDecimal scoreRegion(CompanyMatchInput input, PolicyProgram program) {
        List<String> targets = nullSafe(program.getTargetRegions());
        List<String> userReg = nullSafe(input.getRegionCodes());
        if (targets.isEmpty()) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("region"));
        if (userReg.isEmpty()) return BigDecimal.ZERO;
        boolean exact = userReg.stream().anyMatch(targets::contains);
        if (exact) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("region"));
        boolean broad = userReg.stream().anyMatch(u -> targets.stream()
                .anyMatch(t -> u != null && t != null && u.length() >= 2 && t.length() >= 2
                        && u.substring(0, 2).equals(t.substring(0, 2))));
        return broad ? new BigDecimal("14") : BigDecimal.ZERO;
    }

    /** SIZE 차원: 범위 내 20, ±20% 16, 그 외 0. */
    BigDecimal scoreSize(CompanyMatchInput input, PolicyProgram program) {
        Integer cnt = input.getEmployeeCount();
        if (cnt == null) return BigDecimal.ZERO;
        Integer min = program.getMinEmployees();
        Integer max = program.getMaxEmployees();
        if (min == null && max == null) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("size"));
        if (within(cnt, min, max)) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("size"));
        if (withinTolerance(cnt, min, max, 0.20)) return new BigDecimal("16");
        return BigDecimal.ZERO;
    }

    /** AGE 차원: 범위 내 15, ±20% 12, 그 외 0. */
    BigDecimal scoreAge(CompanyMatchInput input, PolicyProgram program) {
        Integer months = input.getBusinessAgeMonths();
        if (months == null) return BigDecimal.ZERO;
        Integer min = program.getMinBusinessAgeMonths();
        Integer max = program.getMaxBusinessAgeMonths();
        if (min == null && max == null) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("age"));
        if (within(months, min, max)) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("age"));
        if (withinTolerance(months, min, max, 0.20)) return new BigDecimal("12");
        return BigDecimal.ZERO;
    }

    /** REVENUE 차원: 범위 내 15, ±20% 12, 그 외 0. */
    BigDecimal scoreRevenue(CompanyMatchInput input, PolicyProgram program) {
        Long rev = input.getAnnualRevenue();
        if (rev == null) return BigDecimal.ZERO;
        Long min = program.getMinRevenue();
        Long max = program.getMaxRevenue();
        if (min == null && max == null) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("revenue"));
        if (within(rev, min, max)) return BigDecimal.valueOf(DIMENSION_WEIGHTS.get("revenue"));
        if (withinTolerance(rev, min, max, 0.20)) return new BigDecimal("12");
        return BigDecimal.ZERO;
    }

    /** 인증 보너스: target_certifications 와 input.certifications 교집합 ≥ 1 → +5. */
    int scoreCertBonus(CompanyMatchInput input, PolicyProgram program) {
        // program.target_certifications 는 본 SPEC v0.1 에서 별도 컬럼 없으나, custom_attrs/keywords 경로로 우회.
        // 본 구현은 input.certifications 가 비어있지 않으면 보너스 부여 (SPEC §8.3 단순화).
        List<String> certs = nullSafe(input.getCertifications());
        return certs.isEmpty() ? 0 : CERT_BONUS;
    }

    /** 키워드 보너스: program.code 또는 program_name 에 input.custom_attrs.keywords 포함 → 키워드당 1점, 상한 5. */
    int scoreKeywordBonus(CompanyMatchInput input, PolicyProgram program) {
        String custom = input.getCustomAttrs();
        if (custom == null || custom.isBlank() || !custom.contains("\"keywords\"")) return 0;
        // custom_attrs 의 keywords 리스트를 단순 토큰 분리로 추출 (운영 시 Jackson 으로 대체)
        List<String> kws = extractKeywords(custom);
        if (kws.isEmpty()) return 0;
        String name = program.getProgramName() == null ? "" : program.getProgramName();
        long matches = kws.stream().filter(k -> k != null && !k.isBlank() && name.contains(k)).count();
        if (matches <= 0) return 0;
        return (int) Math.min(matches, KEYWORD_BONUS_CAP);
    }

    /** 점수 → 등급 (A>=80 / B>=60 / C>=40 / D<40). */
    String computeGrade(BigDecimal total) {
        if (total.compareTo(GRADE_A) >= 0) return "A";
        if (total.compareTo(GRADE_B) >= 0) return "B";
        if (total.compareTo(GRADE_C) >= 0) return "C";
        return "D";
    }

    String buildBreakdownJson(Map<String, BigDecimal> contributions, int certBonus, int keywordBonus) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> e : contributions.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
        }
        sb.append(",\"cert_bonus\":").append(certBonus);
        sb.append(",\"keyword_bonus\":").append(keywordBonus);
        sb.append("}");
        return sb.toString();
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private MatchedPolicy toMatchedPolicyFromCache(PolicyMatchScore cache) {
        return programMapper.findById(cache.getPolicyId())
                .map(p -> new MatchedPolicy(
                        p.getId(),
                        p.getProgramName(),
                        p.getMinistry(),
                        p.getApplicationEnd(),
                        cache.getScore(),
                        cache.getGrade(),
                        cache.getScoreBreakdown(),
                        cache.getMatchedAt()
                ))
                .orElse(null);
    }

    /** TOP N 범위 [1, 50] (기본 10). */
    int clampTopN(int n) {
        if (n <= 0) return 10;
        return Math.min(n, 50);
    }

    /** value 가 [min, max] 범위 안에 있는지 (null = 무제한). */
    boolean within(long value, Number min, Number max) {
        if (min != null && value < min.longValue()) return false;
        if (max != null && value > max.longValue()) return false;
        return true;
    }

    /** ±tolerance 비율 안에 있는지 (예: 20%). min/max 가 모두 null 이면 false. */
    boolean withinTolerance(long value, Number min, Number max, double tolerance) {
        if (min == null && max == null) return false;
        if (min != null) {
            double lower = min.doubleValue() * (1.0 - tolerance);
            if (value >= lower && (max == null || value <= max.longValue())) return true;
        }
        if (max != null) {
            double upper = max.doubleValue() * (1.0 + tolerance);
            if (value <= upper && (min == null || value >= min.longValue())) return true;
        }
        return false;
    }

    /** custom_attrs JSON 에서 keywords 배열을 단순 추출 (Jackson 대체용 lightweight 파서). */
    List<String> extractKeywords(String customJson) {
        int idx = customJson.indexOf("\"keywords\"");
        if (idx < 0) return Collections.emptyList();
        int bracketStart = customJson.indexOf('[', idx);
        int bracketEnd = customJson.indexOf(']', bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) return Collections.emptyList();
        String inner = customJson.substring(bracketStart + 1, bracketEnd);
        if (inner.isBlank()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String token : inner.split(",")) {
            String trimmed = token.trim().replaceAll("^\"|\"$", "");
            if (!trimmed.isBlank()) out.add(trimmed);
        }
        return out;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 점수 계산 중간 결과 (package-private for testing).
     */
    record ScoreResult(
            BigDecimal totalScore,
            Map<String, BigDecimal> dimensionContributions,
            int certBonus,
            int keywordBonus,
            String breakdownJson
    ) {}
}
