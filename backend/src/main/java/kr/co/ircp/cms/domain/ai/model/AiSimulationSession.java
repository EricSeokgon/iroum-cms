package kr.co.ircp.cms.domain.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 시뮬레이션 세션 엔티티.
 *
 * <p>SPEC-CMS-AI-001 — 익명 시뮬레이션 세션. UUID PK.
 * 평문 IP는 절대 저장하지 않으며 {@code clientIpHash}(SHA-256, 64자)만 보관한다.
 * {@code expiresAt}은 DB 생성 컬럼(createdAt + 24h)이므로 읽기 전용이다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSimulationSession {
    private UUID id;
    private String ksicCode;
    private Long capitalAmount;
    private Integer foundingYear;
    private Long revenueAmount;
    private String projectionResult;  // JSONB (JSON 문자열)
    private String pdfStatus;         // NONE / GENERATING / READY / FAILED
    private String clientIpHash;      // SHA-256 hex (64자) — 평문 IP 금지
    private Instant createdAt;
    private Instant expiresAt;        // DB 생성 컬럼 (createdAt + 24h, 읽기 전용)
}
