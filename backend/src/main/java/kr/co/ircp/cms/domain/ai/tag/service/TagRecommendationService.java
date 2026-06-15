package kr.co.ircp.cms.domain.ai.tag.service;

import kr.co.ircp.cms.common.util.IpHashUtil;
import kr.co.ircp.cms.domain.ai.tag.dto.TagFeedbackRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagRecommendRequest;
import kr.co.ircp.cms.domain.ai.tag.dto.TagRecommendResponse;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationRequest;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 태그 추천 오케스트레이션 서비스.
 *
 * <p>SPEC-CMS-AI-004 — 최소 길이 가드(REQ-AI-TAG-008) → 캐시 조회(REQ-AI-TAG-010) →
 * ML 호출 → 그레이스풀 폴백(빈 배열 200, REQ-AI-TAG-009) → 비동기 로깅(REQ-AI-TAG-011)의
 * 흐름을 담당한다. 세션·본문은 SHA-256 해시로만 다루며 평문을 저장하지 않는다(REQ-AI-TAG-013).
 */
@Service
public class TagRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(TagRecommendationService.class);

    /** 추천 트리거 최소 본문 길이 (REQ-AI-TAG-008). */
    private static final int MIN_CONTENT_LENGTH = 20;
    /** 최대 추천 태그 수 (REQ-AI-TAG-006). */
    private static final int MAX_RECOMMENDATIONS = 5;
    private static final String CACHE_NAME = "tagRecommendationCache";
    private static final String DEFAULT_CONTENT_TYPE = "POST";

    private final MlServiceClient mlServiceClient;
    private final AiTagRecommendationLogService logService;
    private final CacheManager cacheManager;

    public TagRecommendationService(MlServiceClient mlServiceClient,
                                    AiTagRecommendationLogService logService,
                                    CacheManager cacheManager) {
        this.mlServiceClient = mlServiceClient;
        this.logService = logService;
        this.cacheManager = cacheManager;
    }

    /**
     * 본문 내용을 ML 서비스에 전달해 태그 추천 결과를 반환한다.
     *
     * <p>ML 장애 시 빈 배열을 HTTP 200으로 반환하여 오류를 노출하지 않는다(그레이스풀 폴백).
     */
    // @MX:NOTE: [AUTO] 그레이스풀 폴백 — ML 장애 시 빈 배열 200 반환, 오류 미노출 (REQ-AI-TAG-009)
    // @MX:SPEC: SPEC-CMS-AI-004
    public TagRecommendResponse recommendTags(TagRecommendRequest request, String clientIp) {
        // 최소 길이 가드 — ML 미호출 (REQ-AI-TAG-008)
        if (request.content() == null || request.content().length() < MIN_CONTENT_LENGTH) {
            return TagRecommendResponse.empty();
        }

        String contentType = resolveContentType(request.contentType());
        String contentHash = IpHashUtil.sha256Hex(request.content());
        String sessionRef = IpHashUtil.sha256Hex(clientIp);
        List<String> existingTags = request.existingTags() == null
                ? List.of() : request.existingTags();

        // 캐시 조회 (REQ-AI-TAG-010) — 동일 본문 30분 이내 재요청 시 ML 미호출
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            TagRecommendResponse cached = cache.get(contentHash, TagRecommendResponse.class);
            if (cached != null) {
                return cached;
            }
        }

        // ML 호출 + 그레이스풀 폴백 (REQ-AI-TAG-009)
        try {
            TagRecommendationResponse mlResp = mlServiceClient.tagRecommendation(
                    new TagRecommendationRequest(request.content(), existingTags, MAX_RECOMMENDATIONS));
            List<String> tags = mlResp.recommendedTags() == null ? List.of()
                    : mlResp.recommendedTags().stream()
                            .filter(tag -> !existingTags.contains(tag))
                            .limit(MAX_RECOMMENDATIONS)
                            .toList();
            TagRecommendResponse result = new TagRecommendResponse(tags);

            if (cache != null) {
                cache.put(contentHash, result);
            }
            // 추천 이벤트 비동기 적재 (REQ-AI-TAG-011)
            logService.logSuggested(sessionRef, contentType, contentHash,
                    tags, mlResp.scores(), mlResp.modelVersion());
            return result;
        } catch (MlServiceException e) {
            // ML 장애 — 빈 배열 200, 사용자에게 오류 미노출 (REQ-AI-TAG-009)
            log.debug("ML 태그 추천 비활성화 — 그레이스풀 폴백: {}", e.getMessage());
            return TagRecommendResponse.empty();
        }
    }

    /**
     * 태그 추천 채택/거부 피드백을 비동기로 적재한다 (REQ-AI-TAG-012).
     */
    public void recordFeedback(TagFeedbackRequest request, String clientIp) {
        // content_hash는 NOT NULL — 본문 미전송 시 빈 문자열 placeholder 해시 사용
        String contentForHash = request.content() == null || request.content().isBlank()
                ? "tag-feedback" : request.content();
        String contentHash = IpHashUtil.sha256Hex(contentForHash);
        String sessionRef = IpHashUtil.sha256Hex(clientIp);
        logService.logFeedback(sessionRef, resolveContentType(request.contentType()),
                contentHash, request.eventType(), request.tagValue());
    }

    private String resolveContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType;
    }
}
