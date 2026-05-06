package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.MatchResponse;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncident;
import kr.co.ircp.cms.domain.safety.entity.SafetyIncidentKeyword;
import kr.co.ircp.cms.domain.safety.entity.SafetyKeyword;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentKeywordMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyIncidentMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyKeywordMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyMatchResultMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SafetyMatchingService 매칭 알고리즘 단위 테스트.
 * REQ-SAFETY-002 (가중치 키워드 매칭)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyMatchingService — 매칭 알고리즘 (REQ-SAFETY-002)")
class SafetyMatchingServiceTest {

    @Mock private CompanySafetyProfileMapper profileMapper;
    @Mock private SafetyKeywordMapper keywordMapper;
    @Mock private SafetyIncidentMapper incidentMapper;
    @Mock private SafetyIncidentKeywordMapper incidentKeywordMapper;
    @Mock private SafetyMatchResultMapper matchResultMapper;

    private SafetyMatchingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SafetyMatchingServiceImpl(
                profileMapper, keywordMapper, incidentMapper,
                incidentKeywordMapper, matchResultMapper
        );
    }

    private CompanySafetyProfile sampleProfile() {
        return CompanySafetyProfile.builder()
                .id(100L).companyId(10L)
                .industryCode("F4521").subIndustry("건설업")
                .primaryProcess("고소작업")
                .hazardFactors("[\"추락\",\"중장비\"]")
                .riskGrade("D")
                .build();
    }

    private SafetyKeyword keyword(long id, String category, String code, String term) {
        return SafetyKeyword.builder()
                .id(id).category(category).code(code).term(term).status("ACTIVE")
                .build();
    }

    private SafetyIncident incident(long id, String industry, String type, String severity) {
        return SafetyIncident.builder()
                .id(id).industryCode(industry).incidentType(type).severity(severity)
                .occurredAt(Instant.now()).status("PUBLISHED").summary("요약")
                .casualties(1).sourceType("MANUAL")
                .build();
    }

    private SafetyIncidentKeyword mapping(long incidentId, long keywordId, String category, double weight) {
        return new SafetyIncidentKeyword(incidentId, keywordId, BigDecimal.valueOf(weight), category);
    }

    // ──────────────────────────────────────────────
    // REQ-SAFETY-002-D-1: 프로필 → 키워드 매칭
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("프로필 미존재 시 SafetyProfileNotFoundException")
    void match_profileMissing_throwsException() {
        when(profileMapper.findByCompanyId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchForCompany(99L, 5))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    @Test
    @DisplayName("프로필 토큰에서 빈 결과 → 빈 매칭 응답")
    void match_noKeywordsFound_returnsEmpty() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), anyInt())).thenReturn(List.of());
        when(keywordMapper.findMatchingKeywords(anyList())).thenReturn(List.of());

        MatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.results()).isEmpty();
        assertThat(response.fromCache()).isFalse();
        verify(matchResultMapper, never()).insert(any());
    }

    @Test
    @DisplayName("키워드 매칭 + 가중합 score 정규화 [0,1]")
    void match_weightedScore_inRange() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), anyInt())).thenReturn(List.of());

        // 프로필 키워드: INDUSTRY=1개, PROCESS=1개, HAZARD=1개
        SafetyKeyword industryK = keyword(1L, "INDUSTRY", "F4521", "건설업");
        SafetyKeyword processK = keyword(2L, "PROCESS", "HIGH_WORK", "고소작업");
        SafetyKeyword hazardK = keyword(3L, "HAZARD", "FALL", "추락");
        when(keywordMapper.findMatchingKeywords(anyList()))
                .thenReturn(List.of(industryK, processK, hazardK));

        SafetyIncident inc = incident(50L, "F4521", "FALL", "FATAL");
        when(incidentMapper.findCandidatesForMatching(anyList(), anyString())).thenReturn(List.of(inc));

        // 사고 50번에 industry/process/hazard 모두 매핑됨
        when(incidentKeywordMapper.findKeywordsByIncidentId(50L)).thenReturn(List.of(
                mapping(50L, 1L, "INDUSTRY", 1.0),
                mapping(50L, 2L, "PROCESS", 1.0),
                mapping(50L, 3L, "HAZARD", 1.0)
        ));

        MatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).similarityScore())
                .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        // 0.4 + 0.3 + 0.2 = 0.9 (EQUIPMENT 매칭 없음)
        assertThat(response.results().get(0).similarityScore())
                .isEqualByComparingTo(new BigDecimal("0.90"));
    }

    @Test
    @DisplayName("매칭 사유 JSON 포함 — 기여도 contributions 배열")
    void match_reasonJsonContainsContributions() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), anyInt())).thenReturn(List.of());

        SafetyKeyword industryK = keyword(1L, "INDUSTRY", "F4521", "건설업");
        when(keywordMapper.findMatchingKeywords(anyList())).thenReturn(List.of(industryK));

        SafetyIncident inc = incident(50L, "F4521", "FALL", "FATAL");
        when(incidentMapper.findCandidatesForMatching(anyList(), anyString())).thenReturn(List.of(inc));
        when(incidentKeywordMapper.findKeywordsByIncidentId(50L)).thenReturn(List.of(
                mapping(50L, 1L, "INDUSTRY", 1.0)
        ));

        MatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.results()).hasSize(1);
        String reason = response.results().get(0).matchReason();
        assertThat(reason).contains("contributions");
        assertThat(reason).contains("INDUSTRY");
        assertThat(reason).contains("score");
    }

    // ──────────────────────────────────────────────
    // REQ-SAFETY-002-D-3: TOP N 정렬
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("TOP N=2 — 점수 내림차순 정렬, 상위 2건만 반환")
    void match_topN_sortsByScoreDesc() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), anyInt())).thenReturn(List.of());

        SafetyKeyword industryK = keyword(1L, "INDUSTRY", "F4521", "건설업");
        SafetyKeyword processK = keyword(2L, "PROCESS", "HIGH", "고소작업");
        when(keywordMapper.findMatchingKeywords(anyList())).thenReturn(List.of(industryK, processK));

        SafetyIncident a = incident(50L, "F4521", "FALL", "FATAL");
        SafetyIncident b = incident(51L, "F4521", "TRAP", "MINOR");
        SafetyIncident c = incident(52L, "ZOTHER", "FIRE", "MATERIAL");
        when(incidentMapper.findCandidatesForMatching(anyList(), anyString()))
                .thenReturn(List.of(a, b, c));

        // a: industry+process → 0.4+0.3 = 0.7
        when(incidentKeywordMapper.findKeywordsByIncidentId(50L)).thenReturn(List.of(
                mapping(50L, 1L, "INDUSTRY", 1.0),
                mapping(50L, 2L, "PROCESS", 1.0)
        ));
        // b: industry only → 0.4
        when(incidentKeywordMapper.findKeywordsByIncidentId(51L)).thenReturn(List.of(
                mapping(51L, 1L, "INDUSTRY", 1.0)
        ));
        // c: process only → 0.3
        when(incidentKeywordMapper.findKeywordsByIncidentId(52L)).thenReturn(List.of(
                mapping(52L, 2L, "PROCESS", 1.0)
        ));

        MatchResponse response = service.matchForCompany(10L, 2);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).incidentId()).isEqualTo(50L);
        assertThat(response.results().get(1).incidentId()).isEqualTo(51L);
        assertThat(response.results().get(0).similarityScore())
                .isGreaterThan(response.results().get(1).similarityScore());
    }

    // ──────────────────────────────────────────────
    // REQ-SAFETY-002-D-5: TTL 캐시 hit
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("캐시 hit 시 fromCache=true 반환, DB 매칭 미실행")
    void match_cacheHit_returnsFromCacheTrue() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));

        var cache = kr.co.ircp.cms.domain.safety.entity.SafetyMatchResult.builder()
                .id(1L).companyProfileId(100L).incidentId(50L)
                .similarityScore(new BigDecimal("0.78"))
                .matchReason("{\"score\":0.78}")
                .generatedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        // 5건 요청에 대해 5건 캐시 반환 (cache hit)
        List<kr.co.ircp.cms.domain.safety.entity.SafetyMatchResult> cacheList = new ArrayList<>();
        for (int i = 0; i < 5; i++) cacheList.add(cache);
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), eq(5))).thenReturn(cacheList);
        when(incidentMapper.findById(50L)).thenReturn(Optional.of(incident(50L, "F4521", "FALL", "FATAL")));

        MatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.fromCache()).isTrue();
        assertThat(response.results()).hasSize(5);
        // DB 매칭 함수 호출 없음
        verify(keywordMapper, never()).findMatchingKeywords(anyList());
        verify(incidentMapper, never()).findCandidatesForMatching(anyList(), anyString());
    }

    @Test
    @DisplayName("캐시 부족 시 신규 매칭 실행 + 캐시 저장")
    void match_cacheMiss_runsMatchingAndSavesCache() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), anyInt())).thenReturn(List.of());

        SafetyKeyword industryK = keyword(1L, "INDUSTRY", "F4521", "건설업");
        when(keywordMapper.findMatchingKeywords(anyList())).thenReturn(List.of(industryK));

        SafetyIncident inc = incident(50L, "F4521", "FALL", "FATAL");
        when(incidentMapper.findCandidatesForMatching(anyList(), anyString())).thenReturn(List.of(inc));
        when(incidentKeywordMapper.findKeywordsByIncidentId(50L)).thenReturn(List.of(
                mapping(50L, 1L, "INDUSTRY", 1.0)
        ));

        MatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.fromCache()).isFalse();
        assertThat(response.results()).hasSize(1);
        verify(matchResultMapper, times(1)).insert(any());
    }

    // ──────────────────────────────────────────────
    // 토큰 추출 단위 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("프로필 토큰 추출 — industry/sub_industry/process/hazard 모두 포함")
    void extractProfileTokens_includesAllFields() {
        CompanySafetyProfile profile = sampleProfile();

        List<String> tokens = service.extractProfileTokens(profile);

        assertThat(tokens).contains("F4521");
        assertThat(tokens).contains("건설업");
        assertThat(tokens).contains("고소작업");
        assertThat(tokens).contains("추락");
        assertThat(tokens).contains("중장비");
    }

    @Test
    @DisplayName("프로필 토큰 추출 — null/blank 필드 안전 처리")
    void extractProfileTokens_handlesNullsSafely() {
        CompanySafetyProfile profile = CompanySafetyProfile.builder()
                .id(1L).companyId(1L).industryCode("F4521").build();

        List<String> tokens = service.extractProfileTokens(profile);

        assertThat(tokens).containsExactly("F4521");
    }

    @Test
    @DisplayName("getCachedForProfile — 빈 캐시 시 빈 결과")
    void getCached_noCache_returnsEmpty() {
        when(matchResultMapper.findActiveCacheByProfileId(eq(100L), anyInt())).thenReturn(List.of());

        MatchResponse response = service.getCachedForProfile(100L, 5);

        assertThat(response.fromCache()).isTrue();
        assertThat(response.results()).isEmpty();
    }

    @Test
    @DisplayName("topNOrDefault — 1~20 범위 강제, null=5")
    void matchRequest_topN_clampsToRange() {
        assertThat(new kr.co.ircp.cms.domain.safety.dto.MatchRequest(null).topNOrDefault()).isEqualTo(5);
        assertThat(new kr.co.ircp.cms.domain.safety.dto.MatchRequest(0).topNOrDefault()).isEqualTo(1);
        assertThat(new kr.co.ircp.cms.domain.safety.dto.MatchRequest(50).topNOrDefault()).isEqualTo(20);
        assertThat(new kr.co.ircp.cms.domain.safety.dto.MatchRequest(7).topNOrDefault()).isEqualTo(7);
    }
}
