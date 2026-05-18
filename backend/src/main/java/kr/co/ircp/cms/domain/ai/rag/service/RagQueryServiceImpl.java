package kr.co.ircp.cms.domain.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.common.util.IpHashUtil;
import kr.co.ircp.cms.domain.ai.rag.config.RagProperties;
import kr.co.ircp.cms.domain.ai.rag.dto.RagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryRequest;
import kr.co.ircp.cms.domain.ai.rag.dto.RagQueryResponse;
import kr.co.ircp.cms.domain.ai.rag.dto.RagSource;
import kr.co.ircp.cms.domain.ai.rag.entity.AiRagQueryLog;
import kr.co.ircp.cms.domain.ai.rag.repository.PolicyEmbeddingRepository;
import kr.co.ircp.cms.domain.ai.rag.repository.RagQueryLogRepository;
import kr.co.ircp.cms.domain.search.dto.DocResult;
import kr.co.ircp.cms.domain.search.dto.SearchRequest;
import kr.co.ircp.cms.domain.search.dto.SearchResponse;
import kr.co.ircp.cms.domain.search.service.SearchService;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.EmbedRequest;
import kr.co.ircp.cms.infra.ml.dto.EmbedResponse;
import kr.co.ircp.cms.infra.ml.dto.RagContextItem;
import kr.co.ircp.cms.infra.ml.dto.RagRequest;
import kr.co.ircp.cms.infra.ml.dto.RagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RAG 자연어 질의응답 오케스트레이션 구현체.
 *
 * <p>SPEC-CMS-AI-003 — 임베딩 → pgvector → FTS 하이브리드 재랭킹 → LLM 생성 →
 * 폴백 → 캐시 → 비동기 로그. ML 장애 시 503 미반환, FTS 단독 200(REQ-RAG-008~010).
 */
// @MX:ANCHOR: [AUTO] RagQueryServiceImpl — RAG 파이프라인 단일 진입점 (controller·cache·로그 fan_in≥3)
// @MX:REASON: REQ-RAG-001~014 폴백/캐시/PII 불변식의 핵심. degraded 정의 변경 시 AC-RAG-002/007 회귀
// @MX:SPEC: SPEC-CMS-AI-003
@Service
public class RagQueryServiceImpl implements RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CACHE_NAME = "ragQueryCache";
    private static final String NO_RESULT_MESSAGE = "관련 정책을 찾지 못했습니다. 검색어를 바꾸어 다시 시도해 주세요.";
    private static final String DEGRADED_PREFIX = "[간소 검색 결과] AI 생성 답변을 일시적으로 제공할 수 없어 키워드 검색 결과를 안내합니다. ";
    private static final Set<String> VALID_FEEDBACK = Set.of("HELPFUL", "UNHELPFUL");

    private final MlServiceClient mlServiceClient;
    private final PolicyEmbeddingRepository embeddingRepository;
    private final SearchService searchService;
    private final RagQueryLogService logService;
    private final RagQueryLogRepository logRepository;
    private final CacheManager cacheManager;
    private final RagProperties props;

    public RagQueryServiceImpl(MlServiceClient mlServiceClient,
                               PolicyEmbeddingRepository embeddingRepository,
                               SearchService searchService,
                               RagQueryLogService logService,
                               RagQueryLogRepository logRepository,
                               CacheManager cacheManager,
                               RagProperties props) {
        this.mlServiceClient = mlServiceClient;
        this.embeddingRepository = embeddingRepository;
        this.searchService = searchService;
        this.logService = logService;
        this.logRepository = logRepository;
        this.cacheManager = cacheManager;
        this.props = props;
    }

    @Override
    public RagQueryResponse query(RagQueryRequest req, String rawSessionRef, Authentication auth) {
        long startNanos = System.nanoTime();

        // 1. 입력 검증 + 정규화 (REQ-RAG-001, AC-RAG-009)
        String normalized = normalizeAndValidate(req);

        // 2. 식별자 해시 (REQ-RAG-018, 평문 미저장)
        String questionHash = IpHashUtil.sha256Hex(normalized);
        String sessionRef = IpHashUtil.sha256Hex(
                rawSessionRef == null || rawSessionRef.isBlank() ? "anonymous" : rawSessionRef);

        // 3. 캐시 조회 (REQ-RAG-011) — degraded 응답은 애초에 저장되지 않으므로 항상 정상 응답
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            RagQueryResponse cached = cache.get(questionHash, RagQueryResponse.class);
            if (cached != null) {
                RagQueryResponse hit = cached.asCached();
                logService.logQueryAsync(buildLog(hit, questionHash, sessionRef,
                        elapsedMs(startNanos), true));
                return hit;
            }
        }

        // 4. 질문 임베딩 (REQ-RAG-002). 실패 시 pgvector 스킵 + degraded (REQ-RAG-009).
        boolean degraded = false;
        List<Candidate> vectorCandidates = new ArrayList<>();
        try {
            EmbedResponse embed = mlServiceClient.embed(new EmbedRequest(normalized));
            vectorCandidates = pgVectorSearch(embed);
        } catch (MlServiceException e) {
            log.warn("RAG embed 폴백 (pgvector 스킵, FTS 단독): {}", e.getMessage());
            degraded = true;
        }

        // 5. FTS 검색 — 하이브리드 재랭킹 입력 겸 폴백 (SPEC-CMS-006 읽기 전용)
        List<Candidate> ftsCandidates = ftsSearch(normalized);

        // 6. 하이브리드 재랭킹 → 상위 K (REQ-RAG-006/007)
        int topK = clampTopK();
        List<Candidate> ranked = hybridRerank(vectorCandidates, ftsCandidates, topK);

        // 7. 생성형 답변 (REQ-RAG-004). degraded(임베딩 실패)거나 RAG 실패 시 FTS 안내.
        String answer;
        if (ranked.isEmpty()) {
            answer = NO_RESULT_MESSAGE; // 환각 금지 (AC-RAG-008)
        } else if (degraded) {
            answer = degradedAnswer(ranked);
        } else {
            try {
                RagResponse rag = mlServiceClient.rag(new RagRequest(
                        normalized, toContexts(ranked)));
                answer = rag != null && rag.answer() != null && !rag.answer().isBlank()
                        ? rag.answer() : degradedAnswer(ranked);
            } catch (MlServiceException e) {
                log.warn("RAG 생성 폴백 (FTS 컨텍스트 안내): {}", e.getMessage());
                degraded = true;
                answer = degradedAnswer(ranked);
            }
        }

        // 8. 응답 구성
        String queryRef = UUID.randomUUID().toString();
        List<RagSource> sources = ranked.stream()
                .map(c -> new RagSource(c.id, c.title, round(c.score)))
                .toList();
        RagQueryResponse response = new RagQueryResponse(
                answer, sources, degraded, false, queryRef);

        // 9. degraded가 아니면 캐시 적재 (REQ-RAG-012 — degraded는 미저장)
        if (!degraded && cache != null) {
            cache.put(questionHash, response);
        }

        // 10. 비동기 로그 적재 (REQ-RAG-014)
        logService.logQueryAsync(buildLog(response, questionHash, sessionRef,
                elapsedMs(startNanos), false));

        return response;
    }

    @Override
    public void feedback(RagFeedbackRequest req) {
        if (req == null || req.feedback() == null
                || !VALID_FEEDBACK.contains(req.feedback())) {
            throw new IllegalArgumentException(
                    "피드백 값은 HELPFUL 또는 UNHELPFUL만 허용됩니다");
        }
        if (req.queryRef() == null || req.queryRef().isBlank()) {
            throw new IllegalArgumentException("queryRef는 필수입니다");
        }
        int updated = logRepository.updateFeedback(req.queryRef(), req.feedback());
        if (updated == 0) {
            // 비동기 적재 지연으로 행이 아직 없을 수 있음 — 멱등(에러 대신 무시).
            log.debug("RAG 피드백 대상 행 미존재(비동기 적재 지연 가능): queryRef={}",
                    req.queryRef());
        }
    }

    // ─── 파이프라인 헬퍼 ──────────────────────────────────────────────

    private String normalizeAndValidate(RagQueryRequest req) {
        String raw = req == null ? null : req.question();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("질문은 비어 있을 수 없습니다");
        }
        String normalized = raw.trim().replaceAll("\\s+", " ");
        if (normalized.length() > props.getMaxQuestionLength()) {
            throw new IllegalArgumentException(
                    "질문은 " + props.getMaxQuestionLength() + "자를 초과할 수 없습니다");
        }
        return normalized;
    }

    /** pgvector cosine 검색 (REQ-RAG-003). */
    // @MX:WARN: [AUTO] pgvector p95 > 1s 시 Milvus 마이그레이션 트리거 기준 초과
    // @MX:REASON: 벡터 검색 p95 < 1s SLA 요구사항 (SPEC-CMS-AI-003)
    // @MX:SPEC: SPEC-CMS-AI-003
    private List<Candidate> pgVectorSearch(EmbedResponse embed) {
        if (embed == null || embed.vector() == null || embed.vector().isEmpty()) {
            return List.of();
        }
        String literal = toVectorLiteral(embed.vector());
        List<Map<String, Object>> rows = embeddingRepository.searchByCosine(
                literal, clampTopK() * 2); // 재랭킹 여유 풀
        List<Candidate> out = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(rows)) {
            out.add(new Candidate(
                    asLong(row.get("id")),
                    asString(row.get("title")),
                    asString(row.get("content")),
                    asDouble(row.get("score"))));
        }
        return out;
    }

    /** FTS 단독 검색 (폴백/하이브리드 입력, SPEC-CMS-006 읽기 전용). */
    private List<Candidate> ftsSearch(String normalized) {
        try {
            SearchResponse fts = searchService.search(
                    new SearchRequest(normalized, "policy", 1, clampTopK() * 2, "ko"),
                    null, false, null, null);
            List<Candidate> out = new ArrayList<>();
            for (DocResult doc : nullSafe(fts == null ? null : fts.content())) {
                out.add(new Candidate(doc.docId(), doc.title(),
                        doc.snippet() == null ? "" : doc.snippet(), doc.rank()));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("RAG FTS 폴백 검색 실패 (빈 결과로 진행): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 하이브리드 재랭킹: vector·fts 점수를 정규화 가중 결합 후 내림차순 Top-K (REQ-RAG-006).
     */
    private List<Candidate> hybridRerank(List<Candidate> vector,
                                         List<Candidate> fts, int topK) {
        Map<Long, Candidate> vById = byId(vector);
        Map<Long, Candidate> fById = byId(fts);
        double maxV = maxScore(vector);
        double maxF = maxScore(fts);

        Map<Long, Candidate> merged = new LinkedHashMap<>();
        for (Candidate c : vector) {
            merged.putIfAbsent(c.id, c);
        }
        for (Candidate c : fts) {
            merged.putIfAbsent(c.id, c);
        }

        List<Candidate> out = new ArrayList<>();
        for (Candidate base : merged.values()) {
            double vNorm = vById.containsKey(base.id) ? norm(vById.get(base.id).score, maxV) : 0.0;
            double fNorm = fById.containsKey(base.id) ? norm(fById.get(base.id).score, maxF) : 0.0;
            double hybrid = props.getWVector() * vNorm + props.getWFts() * fNorm;
            Candidate withTitle = base.title != null ? base
                    : firstNonNullTitle(vById.get(base.id), fById.get(base.id), base);
            out.add(new Candidate(base.id, withTitle.title,
                    pickContent(vById.get(base.id), fById.get(base.id)), hybrid));
        }
        out.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        return out.size() > topK ? new ArrayList<>(out.subList(0, topK)) : out;
    }

    private List<RagContextItem> toContexts(List<Candidate> ranked) {
        List<RagContextItem> ctx = new ArrayList<>();
        for (Candidate c : ranked) {
            ctx.add(new RagContextItem(c.id, c.title == null ? "" : c.title,
                    c.content == null ? "" : c.content));
        }
        return ctx;
    }

    private String degradedAnswer(List<Candidate> ranked) {
        StringBuilder sb = new StringBuilder(DEGRADED_PREFIX);
        for (int i = 0; i < ranked.size(); i++) {
            sb.append(i + 1).append(". ").append(ranked.get(i).title);
            if (i < ranked.size() - 1) {
                sb.append(" / ");
            }
        }
        return sb.toString();
    }

    private AiRagQueryLog buildLog(RagQueryResponse resp, String questionHash,
                                   String sessionRef, int latencyMs, boolean cacheHit) {
        return AiRagQueryLog.builder()
                .queryRef(resp.queryRef())
                .questionHash(questionHash)
                .sessionRef(sessionRef)
                .retrievedPolicyIds(toJson(resp.sources().stream()
                        .map(RagSource::id).toList()))
                .answerQualityScore(null)
                .latencyMs(latencyMs)
                .cacheHit(cacheHit)
                .degraded(resp.degraded())
                .build();
    }

    // ─── 순수 유틸 ────────────────────────────────────────────────────

    private int clampTopK() {
        int def = props.getTopKDefault();
        return Math.min(Math.max(def, 1), props.getTopKMax());
    }

    private static String toVectorLiteral(List<Float> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector.get(i));
        }
        return sb.append(']').toString();
    }

    private static Map<Long, Candidate> byId(List<Candidate> list) {
        Map<Long, Candidate> m = new LinkedHashMap<>();
        for (Candidate c : list) {
            m.putIfAbsent(c.id, c);
        }
        return m;
    }

    private static double maxScore(List<Candidate> list) {
        double max = 0.0;
        for (Candidate c : list) {
            max = Math.max(max, c.score);
        }
        return max;
    }

    private static double norm(double v, double max) {
        return max <= 0.0 ? 0.0 : v / max;
    }

    private static Candidate firstNonNullTitle(Candidate a, Candidate b, Candidate fallback) {
        if (a != null && a.title != null) {
            return a;
        }
        if (b != null && b.title != null) {
            return b;
        }
        return fallback;
    }

    private static String pickContent(Candidate a, Candidate b) {
        if (a != null && a.content != null && !a.content.isBlank()) {
            return a.content;
        }
        if (b != null && b.content != null) {
            return b.content;
        }
        return "";
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return o == null ? null : Long.valueOf(o.toString());
    }

    private static Double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return o == null ? 0.0 : Double.valueOf(o.toString());
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /** 재랭킹 후보 (vector/fts 공통). */
    private record Candidate(Long id, String title, String content, double score) {
    }
}
