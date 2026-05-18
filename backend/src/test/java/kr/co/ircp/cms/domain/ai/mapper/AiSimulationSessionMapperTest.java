package kr.co.ircp.cms.domain.ai.mapper;

import kr.co.ircp.cms.domain.ai.model.AiSimulationSession;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-AI-001 Step 1 — ai_simulation_session MyBatis 매퍼 RED 테스트.
 *
 * <p>UUID PK 세션 생성/조회, 만료 시각(24h) 검증, IP는 SHA-256 해시만 저장됨을 검증한다.
 */
// @MX:SPEC: SPEC-CMS-AI-001
@DisplayName("AiSimulationSessionMapper IT (SPEC-CMS-AI-001)")
class AiSimulationSessionMapperTest extends AbstractIntegrationTest {

    @Autowired AiSimulationSessionMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    /** 64자 16진수 SHA-256 해시 예시 (평문 IP 절대 미저장). */
    private static final String IP_HASH =
            "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b";

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ai_simulation_session");
    }

    @Test
    @DisplayName("UUID 세션을 생성하고 findById(UUID)로 조회한다")
    void createAndFindBySessionId() {
        AiSimulationSession session = AiSimulationSession.builder()
                .ksicCode("62010")
                .capitalAmount(50_000_000L)
                .foundingYear(2020)
                .revenueAmount(120_000_000L)
                .pdfStatus("NONE")
                .clientIpHash(IP_HASH)
                .build();

        mapper.insert(session);

        UUID id = session.getId();
        assertThat(id).isNotNull();

        Optional<AiSimulationSession> found = mapper.findById(id);
        assertThat(found).isPresent();
        AiSimulationSession actual = found.get();
        assertThat(actual.getKsicCode()).isEqualTo("62010");
        assertThat(actual.getCapitalAmount()).isEqualTo(50_000_000L);
        assertThat(actual.getFoundingYear()).isEqualTo(2020);
        assertThat(actual.getPdfStatus()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("expires_at은 created_at + 24시간으로 자동 계산된다")
    void expiresAt24HoursAfterCreate() {
        AiSimulationSession session = AiSimulationSession.builder()
                .ksicCode("47")
                .capitalAmount(10_000_000L)
                .foundingYear(2019)
                .clientIpHash(IP_HASH)
                .build();
        mapper.insert(session);

        Optional<AiSimulationSession> found = mapper.findById(session.getId());
        assertThat(found).isPresent();
        Instant createdAt = found.get().getCreatedAt();
        Instant expiresAt = found.get().getExpiresAt();
        assertThat(createdAt).isNotNull();
        assertThat(expiresAt).isNotNull();
        long diffHours = java.time.Duration.between(createdAt, expiresAt).toHours();
        assertThat(diffHours).isEqualTo(24);
    }

    @Test
    @DisplayName("client_ip_hash는 SHA-256 해시(64자)만 저장된다 - 평문 IP 없음")
    void onlyIpHashStored() {
        AiSimulationSession session = AiSimulationSession.builder()
                .ksicCode("62010")
                .capitalAmount(50_000_000L)
                .foundingYear(2020)
                .clientIpHash(IP_HASH)
                .build();
        mapper.insert(session);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT client_ip_hash FROM ai_simulation_session WHERE id = ?",
                String.class, session.getId());
        assertThat(storedHash).isEqualTo(IP_HASH);
        assertThat(storedHash).hasSize(64);
        // 평문 IP 패턴이 절대 들어가지 않음
        assertThat(storedHash).doesNotContain(".");
    }

    @Test
    @DisplayName("updatePdfStatus로 PDF 생성 상태를 전이한다 (NONE -> GENERATING -> READY)")
    void updatePdfStatus() {
        AiSimulationSession session = AiSimulationSession.builder()
                .ksicCode("62010")
                .capitalAmount(50_000_000L)
                .foundingYear(2020)
                .clientIpHash(IP_HASH)
                .build();
        mapper.insert(session);

        assertThat(mapper.updatePdfStatus(session.getId(), "GENERATING")).isEqualTo(1);
        assertThat(mapper.findById(session.getId()).orElseThrow().getPdfStatus())
                .isEqualTo("GENERATING");

        assertThat(mapper.updatePdfStatus(session.getId(), "READY")).isEqualTo(1);
        assertThat(mapper.findById(session.getId()).orElseThrow().getPdfStatus())
                .isEqualTo("READY");
    }

    @Test
    @DisplayName("countByIpHashSince로 특정 IP 해시의 최근 세션 수를 집계한다 (rate limit 기반)")
    void countByIpHashSince() {
        for (int i = 0; i < 3; i++) {
            AiSimulationSession session = AiSimulationSession.builder()
                    .ksicCode("62010")
                    .capitalAmount(50_000_000L)
                    .foundingYear(2020)
                    .clientIpHash(IP_HASH)
                    .build();
            mapper.insert(session);
        }

        long count = mapper.countByIpHashSince(IP_HASH, Instant.now().minusSeconds(3600));
        assertThat(count).isEqualTo(3);

        long otherCount = mapper.countByIpHashSince(
                "0000000000000000000000000000000000000000000000000000000000000000",
                Instant.now().minusSeconds(3600));
        assertThat(otherCount).isZero();
    }
}
