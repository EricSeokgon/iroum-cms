package kr.co.ircp.cms.domain.ai.tag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.ai.tag.mapper.AiTagRecommendationLogMapper;
import kr.co.ircp.cms.domain.ai.tag.model.AiTagRecommendationLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * SPEC-CMS-AI-004 — 태그 추천/피드백 로그 비동기 서비스 단위 테스트 (RED, Docker 불필요).
 *
 * <p>매퍼 위임·JSON 직렬화·event_type 정확성을 검증한다(AC-AI-TAG-011/012).
 */
// @MX:SPEC: SPEC-CMS-AI-004
@DisplayName("태그 추천/피드백 로그 서비스 단위 (SPEC-CMS-AI-004)")
class AiTagRecommendationLogServiceTest {

    private AiTagRecommendationLogMapper mapper;
    private AiTagRecommendationLogService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiTagRecommendationLogMapper.class);
        service = new AiTagRecommendationLogService(mapper, new ObjectMapper());
    }

    @Test
    @DisplayName("AC-AI-TAG-011: logSuggested는 SUGGESTED 행을 매퍼에 위임하고 태그·점수를 JSON으로 직렬화한다")
    void logSuggestedDelegatesToMapper() {
        service.logSuggested("sessHash", "POST", "contentHash",
                List.of("태그1", "태그2"), Map.of("태그1", 0.92), "1.0.0");

        ArgumentCaptor<AiTagRecommendationLog> captor =
                ArgumentCaptor.forClass(AiTagRecommendationLog.class);
        verify(mapper).insertSuggested(captor.capture());

        AiTagRecommendationLog logged = captor.getValue();
        assertThat(logged.getEventType()).isEqualTo("SUGGESTED");
        assertThat(logged.getSessionRef()).isEqualTo("sessHash");
        assertThat(logged.getContentType()).isEqualTo("POST");
        assertThat(logged.getContentHash()).isEqualTo("contentHash");
        assertThat(logged.getRecommendedTags()).contains("태그1", "태그2");
        assertThat(logged.getMlScores()).contains("태그1");
        assertThat(logged.getModelVersion()).isEqualTo("1.0.0");
        assertThat(logged.getTagValue()).isNull();
    }

    @Test
    @DisplayName("AC-AI-TAG-012: logFeedback은 ACCEPTED 행을 tag_value와 함께 매퍼에 위임한다")
    void logFeedbackAcceptedDelegatesToMapper() {
        service.logFeedback("sessHash", "QNA", "contentHash", "ACCEPTED", "태그1");

        ArgumentCaptor<AiTagRecommendationLog> captor =
                ArgumentCaptor.forClass(AiTagRecommendationLog.class);
        verify(mapper).insertFeedback(captor.capture());

        AiTagRecommendationLog logged = captor.getValue();
        assertThat(logged.getEventType()).isEqualTo("ACCEPTED");
        assertThat(logged.getTagValue()).isEqualTo("태그1");
        assertThat(logged.getContentType()).isEqualTo("QNA");
        assertThat(logged.getInteractedAt()).isNotNull();
    }
}
