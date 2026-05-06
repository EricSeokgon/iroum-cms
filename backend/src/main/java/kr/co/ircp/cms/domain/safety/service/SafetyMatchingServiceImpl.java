package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.MatchResponse;
import kr.co.ircp.cms.domain.safety.dto.MatchedIncident;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncident;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncidentKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyMatchResult;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentKeywordMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyKeywordMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyMatchResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 사고사례 매칭 알고리즘 구현체.
 *
 * 1차 알고리즘 (REQ-SAFETY-002):
 *   score = 0.4 * match(INDUSTRY) + 0.3 * match(PROCESS) + 0.2 * match(HAZARD) + 0.1 * match(EQUIPMENT)
 *   match(category) = sum(weight_i for matched keyword i) / max_possible_weight(category)
 * 모든 점수는 [0.00, 1.00] 정규화.
 *
 * // @MX:NOTE: [AUTO] 1차 buy: keyword 정확 일치 + 동의어. v0.2+에서 vector embedding으로 확장 (SPEC-CMS-AI-001 옵션).
 * // @MX:SPEC: REQ-SAFETY-002
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyMatchingServiceImpl implements SafetyMatchingService {

    /** 카테고리 가중치 (REQ-SAFETY-002-D-1). */
    private static final Map<String, BigDecimal> CATEGORY_WEIGHTS = Map.of(
            "INDUSTRY",  new BigDecimal("0.4"),
            "PROCESS",   new BigDecimal("0.3"),
            "HAZARD",    new BigDecimal("0.2"),
            "EQUIPMENT", new BigDecimal("0.1")
    );

    private static final Pattern TOKEN_DELIMITER = Pattern.compile("[\\s,;\\[\\]\"'/]+");

    private final CompanySafetyProfileMapper profileMapper;
    private final SafetyKeywordMapper keywordMapper;
    private final SafetyIncidentMapper incidentMapper;
    private final SafetyIncidentKeywordMapper incidentKeywordMapper;
    private final SafetyMatchResultMapper matchResultMapper;

    @Override
    @Transactional
    public MatchResponse matchForCompany(Long companyId, int topN) {
        CompanySafetyProfile profile = profileMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new SafetyProfileNotFoundException(companyId));

        // 1. TTL 캐시 hit 확인 (REQ-SAFETY-002-D-5)
        List<SafetyMatchResult> cached = matchResultMapper.findActiveCacheByProfileId(profile.getId(), topN);
        if (cached != null && cached.size() >= topN) {
            return new MatchResponse(profile.getId(), topN, true,
                    cached.stream().limit(topN)
                            .map(this::toMatchedIncidentFromCache)
                            .collect(Collectors.toList()));
        }

        // 2. 프로필 → 토큰 추출 → 키워드 매칭
        List<String> profileTokens = extractProfileTokens(profile);
        List<SafetyKeyword> profileKeywords = profileTokens.isEmpty()
                ? List.of()
                : keywordMapper.findMatchingKeywords(profileTokens);

        // 3. 후보 사고사례 가져오기
        List<Long> profileKeywordIds = profileKeywords.stream()
                .map(SafetyKeyword::getId).toList();

        if (profileKeywordIds.isEmpty()) {
            return new MatchResponse(profile.getId(), topN, false, List.of());
        }

        List<SafetyIncident> candidates =
                incidentMapper.findCandidatesForMatching(profileKeywordIds, profile.getIndustryCode());

        // 4. 사고사례별 score 계산
        Map<String, BigDecimal> profileCategoryWeights = profileKeywordsByCategory(profileKeywords);

        List<MatchedIncident> scored = new ArrayList<>();
        for (SafetyIncident incident : candidates) {
            ScoreResult sr = computeScore(incident.getId(), profileKeywordIds, profileCategoryWeights);
            String reason = buildMatchReason(sr, incident);
            scored.add(new MatchedIncident(
                    incident.getId(),
                    incident.getIndustryCode(),
                    incident.getIncidentType(),
                    incident.getSeverity(),
                    incident.getOccurredAt(),
                    incident.getSummary(),
                    sr.totalScore,
                    reason
            ));
        }

        // 5. score 내림차순 정렬 + tiebreak (occurred_at desc, severity)
        scored.sort(Comparator
                .comparing(MatchedIncident::similarityScore, Comparator.reverseOrder())
                .thenComparing(MatchedIncident::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(this::severityRank));

        List<MatchedIncident> topResults = scored.stream().limit(topN).toList();

        // 6. 캐시 저장
        for (MatchedIncident mi : topResults) {
            SafetyMatchResult cache = SafetyMatchResult.builder()
                    .companyProfileId(profile.getId())
                    .incidentId(mi.incidentId())
                    .similarityScore(mi.similarityScore())
                    .matchReason(mi.matchReason())
                    .build();
            matchResultMapper.insert(cache);
        }

        return new MatchResponse(profile.getId(), topN, false, topResults);
    }

    @Override
    public MatchResponse getCachedForProfile(Long profileId, int topN) {
        List<SafetyMatchResult> cached = matchResultMapper.findActiveCacheByProfileId(profileId, topN);
        return new MatchResponse(profileId, topN, true,
                cached.stream().map(this::toMatchedIncidentFromCache).collect(Collectors.toList()));
    }

    // ─── 매칭 알고리즘 헬퍼 ─────────────────────────────────────────────────

    /**
     * 프로필에서 매칭 토큰 추출.
     * industry_code, primary_process, hazard_factors, sub_industry 모두 LOWER 정규화.
     */
    List<String> extractProfileTokens(CompanySafetyProfile profile) {
        List<String> tokens = new ArrayList<>();
        if (profile.getIndustryCode() != null) tokens.add(profile.getIndustryCode().trim());
        if (profile.getSubIndustry() != null) tokens.add(profile.getSubIndustry().trim());
        if (profile.getPrimaryProcess() != null) {
            for (String t : TOKEN_DELIMITER.split(profile.getPrimaryProcess())) {
                if (!t.isBlank()) tokens.add(t.trim());
            }
        }
        if (profile.getHazardFactors() != null) {
            for (String t : TOKEN_DELIMITER.split(profile.getHazardFactors())) {
                String cleaned = t.replaceAll("[\\{\\}\\[\\]\"'`]", "").trim();
                if (!cleaned.isBlank()) tokens.add(cleaned);
            }
        }
        return tokens.stream().distinct().filter(s -> !s.isBlank()).toList();
    }

    /**
     * 프로필 키워드를 카테고리별로 묶고 가중치 합 계산 (max_possible_weight 분모용).
     */
    Map<String, BigDecimal> profileKeywordsByCategory(List<SafetyKeyword> keywords) {
        Map<String, BigDecimal> bucket = new HashMap<>();
        for (SafetyKeyword k : keywords) {
            bucket.merge(k.getCategory(), BigDecimal.ONE, BigDecimal::add);
        }
        return bucket;
    }

    /**
     * 단일 사고사례에 대한 score 계산.
     * match(category) = (사고-키워드 가중치 합 ∩ 프로필 키워드) / 프로필 카테고리 키워드 수
     */
    ScoreResult computeScore(Long incidentId,
                             List<Long> profileKeywordIds,
                             Map<String, BigDecimal> profileByCategory) {

        List<SafetyIncidentKeyword> incidentKeywords =
                incidentKeywordMapper.findKeywordsByIncidentId(incidentId);

        Map<String, BigDecimal> categoryContribution = new LinkedHashMap<>();
        Map<String, List<Long>> matchedKeywordIds = new LinkedHashMap<>();
        for (String cat : CATEGORY_WEIGHTS.keySet()) {
            categoryContribution.put(cat, BigDecimal.ZERO);
            matchedKeywordIds.put(cat, new ArrayList<>());
        }

        for (SafetyIncidentKeyword ik : incidentKeywords) {
            if (!profileKeywordIds.contains(ik.getKeywordId())) continue;
            String cat = ik.getCategory();
            if (cat == null || !CATEGORY_WEIGHTS.containsKey(cat)) continue;

            BigDecimal w = ik.getWeight() == null ? BigDecimal.ONE : ik.getWeight();
            categoryContribution.merge(cat, w, BigDecimal::add);
            matchedKeywordIds.get(cat).add(ik.getKeywordId());
        }

        // 정규화 + 가중합
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : categoryContribution.entrySet()) {
            String cat = e.getKey();
            BigDecimal raw = e.getValue();
            BigDecimal denom = profileByCategory.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal matchRatio = denom.signum() == 0
                    ? BigDecimal.ZERO
                    : raw.divide(denom, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            BigDecimal contribution = matchRatio.multiply(CATEGORY_WEIGHTS.get(cat))
                    .setScale(4, RoundingMode.HALF_UP);
            normalized.put(cat, contribution);
            total = total.add(contribution);
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ONE) > 0) total = BigDecimal.ONE;

        return new ScoreResult(total, normalized, matchedKeywordIds);
    }

    /**
     * XAI 매칭 사유 JSON 생성.
     * REQ-SAFETY-002-D-4
     */
    String buildMatchReason(ScoreResult sr, SafetyIncident incident) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"score\":").append(sr.totalScore);
        sb.append(",\"contributions\":[");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> e : sr.contributionsByCategory.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            String cat = e.getKey();
            sb.append("{\"category\":\"").append(cat).append("\"");
            sb.append(",\"weight\":").append(CATEGORY_WEIGHTS.get(cat));
            sb.append(",\"contribution\":").append(e.getValue());
            sb.append(",\"matchedKeywordIds\":")
              .append(sr.matchedKeywordIdsByCategory.getOrDefault(cat, List.of()));
            sb.append("}");
        }
        sb.append("],");
        sb.append("\"explain_ko\":\"")
          .append("동일 업종/공정/위험요소 매칭으로 사고사례 ")
          .append(incident.getIncidentType()).append(" (").append(incident.getSeverity()).append(") 매칭됨")
          .append("\"");
        sb.append("}");
        return sb.toString();
    }

    private MatchedIncident toMatchedIncidentFromCache(SafetyMatchResult cache) {
        return incidentMapper.findById(cache.getIncidentId())
                .map(inc -> new MatchedIncident(
                        inc.getId(),
                        inc.getIndustryCode(),
                        inc.getIncidentType(),
                        inc.getSeverity(),
                        inc.getOccurredAt(),
                        inc.getSummary(),
                        cache.getSimilarityScore(),
                        cache.getMatchReason()))
                .orElse(null);
    }

    /** severity tiebreak 순위: FATAL=0 > SEVERE=1 > MINOR=2 > MATERIAL=3 > 기타=4 */
    private int severityRank(MatchedIncident mi) {
        if (mi.severity() == null) return 4;
        return switch (mi.severity()) {
            case "FATAL"    -> 0;
            case "SEVERE"   -> 1;
            case "MINOR"    -> 2;
            case "MATERIAL" -> 3;
            default         -> 4;
        };
    }

    /**
     * 점수 계산 중간 결과 보관용 record (package-private for testing).
     */
    record ScoreResult(
            BigDecimal totalScore,
            Map<String, BigDecimal> contributionsByCategory,
            Map<String, List<Long>> matchedKeywordIdsByCategory
    ) {}
}
