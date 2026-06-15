package kr.co.ircp.cms.infra.ml.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-AI-004 — ML 태그 추천 DTO 계약 단위 테스트 (RED, Docker 불필요).
 *
 * <p>OpenAPI 계약(docs/ai-ml-service-openapi.yaml) {@code POST /ml/v1/tag-recommend}에
 * 정합하는 요청·응답 record 필드 접근을 검증한다. ML 요청에는 PII 없이 본문 텍스트만 포함된다.
 */
// @MX:SPEC: SPEC-CMS-AI-004
@DisplayName("ML 태그 추천 DTO 계약 단위 테스트 (SPEC-CMS-AI-004)")
class TagRecommendationDtoTest {

    @Test
    @DisplayName("TagRecommendationRequest는 content/existingTags/topK 필드를 보유한다")
    void requestFieldsAccessible() {
        TagRecommendationRequest request =
                new TagRecommendationRequest("테스트 내용", List.of(), 5);

        assertThat(request.content()).isEqualTo("테스트 내용");
        assertThat(request.existingTags()).isEmpty();
        assertThat(request.topK()).isEqualTo(5);
    }

    @Test
    @DisplayName("TagRecommendationResponse는 recommendedTags/scores/modelVersion 필드를 보유한다")
    void responseFieldsAccessible() {
        TagRecommendationResponse response = new TagRecommendationResponse(
                List.of("태그1", "태그2"),
                Map.of("태그1", 0.92),
                "1.0.0");

        assertThat(response.recommendedTags()).containsExactly("태그1", "태그2");
        assertThat(response.scores()).containsEntry("태그1", 0.92);
        assertThat(response.modelVersion()).isEqualTo("1.0.0");
    }
}
