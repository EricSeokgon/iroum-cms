package kr.co.ircp.cms.domain.ai.service;

import kr.co.ircp.cms.domain.ai.model.AiSimulationSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PdfGeneratorService 단위 테스트 — SPEC-CMS-SIM-001 확장 검증.
 */
class PdfGeneratorServiceTest {

    private final PdfGeneratorService sut = new PdfGeneratorService();

    @Test
    void generate_3YearHorizon_returnsPdfBytes() {
        // 3년 투영 기본값
        AiSimulationSession session = sessionBuilder()
                .horizonYears(3)
                .projectionResult("{\"projection\":[{\"year\":1,\"stage\":\"seed\",\"entryProbabilities\":{\"low\":0.2}}]}")
                .build();

        byte[] pdf = sut.generateSimulationReport(session);

        assertThat(pdf).isNotEmpty();
        // PDF 파일 매직 바이트 검증
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(pdf[1]).isEqualTo((byte) 'P');
    }

    @Test
    void generate_5YearHorizon_returnsPdfBytes() {
        // SIM-001 — 5년 투영 옵션
        AiSimulationSession session = sessionBuilder()
                .horizonYears(5)
                .projectionResult("{\"projection\":[]}")
                .build();

        byte[] pdf = sut.generateSimulationReport(session);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generate_withEmployeeCount_returnsPdfBytes() {
        // SIM-001 — 직원 수 포함 세션
        AiSimulationSession session = sessionBuilder()
                .employeeCount(5)
                .horizonYears(3)
                .projectionResult(null)
                .build();

        byte[] pdf = sut.generateSimulationReport(session);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generate_withRecommendedPolicies_returnsPdfBytes() {
        // SIM-001 — 추천 정책 번들 섹션 렌더링
        String policies = "[{\"title\":\"창업 지원 정책\",\"type\":\"보조금\",\"supportAmount\":\"1000만원\"}]";
        AiSimulationSession session = sessionBuilder()
                .horizonYears(3)
                .recommendedPolicies(policies)
                .projectionResult("{\"projection\":[]}")
                .build();

        byte[] pdf = sut.generateSimulationReport(session);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generate_withNullProjection_doesNotThrow() {
        // 빈 투영 데이터 — 예외 없이 PDF 생성
        AiSimulationSession session = sessionBuilder()
                .horizonYears(3)
                .projectionResult(null)
                .build();

        byte[] pdf = sut.generateSimulationReport(session);

        assertThat(pdf).isNotEmpty();
    }

    private AiSimulationSession.AiSimulationSessionBuilder sessionBuilder() {
        return AiSimulationSession.builder()
                .id(UUID.randomUUID())
                .ksicCode("G4711")
                .capitalAmount(50_000_000L)
                .foundingYear(2023)
                .revenueAmount(100_000_000L)
                .pdfStatus("NONE")
                .clientIpHash("a".repeat(64))
                .createdAt(Instant.now());
    }
}
