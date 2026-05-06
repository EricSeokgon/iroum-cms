package kr.co.ircp.cms.domain.policy.matching.service;

import kr.co.ircp.cms.domain.policy.matching.dto.CompanyProfileUpsertRequest;
import kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.matching.entity.CompanyMatchInput;
import kr.co.ircp.cms.domain.policy.matching.entity.PolicyMatchScore;
import kr.co.ircp.cms.domain.policy.matching.exception.CompanyMatchInputNotFoundException;
import kr.co.ircp.cms.domain.policy.matching.repository.CompanyMatchInputMapper;
import kr.co.ircp.cms.domain.policy.matching.repository.PolicyMatchScoreMapper;
import kr.co.ircp.cms.domain.policy.program.entity.PolicyProgram;
import kr.co.ircp.cms.domain.policy.program.repository.PolicyProgramMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PolicyMatchingService 매칭 알고리즘 단위 테스트.
 * REQ-POLICY-002 / REQ-POLICY-003
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyMatchingService — 5차원 매칭 알고리즘 (REQ-POLICY-003)")
class PolicyMatchingServiceTest {

    @Mock private CompanyMatchInputMapper companyInputMapper;
    @Mock private PolicyProgramMapper programMapper;
    @Mock private PolicyMatchScoreMapper scoreMapper;

    private PolicyMatchingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PolicyMatchingServiceImpl(companyInputMapper, programMapper, scoreMapper);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private CompanyMatchInput sampleProfile() {
        return CompanyMatchInput.builder()
                .id(1L).companyId(10L)
                .industryCodes(List.of("F4521"))
                .regionCodes(List.of("11000"))
                .employeeCount(50)
                .annualRevenue(5_000_000_000L)
                .businessAgeMonths(60)
                .certifications(List.of("ISO9001"))
                .customAttrs("{\"keywords\":[\"창업\",\"R&D\"]}")
                .build();
    }

    private PolicyProgram fullMatchProgram() {
        return PolicyProgram.builder()
                .id(100L).code("KSP-001").ministry("MSS").programName("창업도약패키지")
                .targetIndustries(List.of("F4521"))
                .targetRegions(List.of("11000"))
                .minEmployees(10).maxEmployees(100)
                .minRevenue(1_000_000_000L).maxRevenue(10_000_000_000L)
                .minBusinessAgeMonths(12).maxBusinessAgeMonths(120)
                .applicationEnd(Instant.now().plusSeconds(7 * 86400))
                .status("ACTIVE")
                .build();
    }

    // ─── REQ-POLICY-003-D-1: 5차원 점수 ────────────────────────────────────

    @Test
    @DisplayName("프로필 미존재 시 CompanyMatchInputNotFoundException")
    void match_profileMissing_throws() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(99L), anyInt())).thenReturn(List.of());
        when(companyInputMapper.findByCompanyId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchForCompany(99L, 5))
                .isInstanceOf(CompanyMatchInputNotFoundException.class);
    }

    @Test
    @DisplayName("5차원 모두 일치 + 인증 + 키워드(창업) 보너스 → 100점 만점")
    void match_allDimensionsMatch_perfectScore() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(fullMatchProgram()));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.results()).hasSize(1);
        // industry 30 + region 20 + size 20 + age 15 + revenue 15 = 100, + cert 5 + keyword 1 → 상한 100
        assertThat(response.results().get(0).score()).isEqualByComparingTo("100.00");
        assertThat(response.results().get(0).grade()).isEqualTo("A");
    }

    @Test
    @DisplayName("INDUSTRY 부분 일치 (앞 3자리) → 15점")
    void match_industryPartialMatch_halfScore() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        CompanyMatchInput profile = sampleProfile();
        profile.setIndustryCodes(List.of("F4599"));  // F45 prefix 일치
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(profile));

        PolicyProgram p = fullMatchProgram();
        p.setTargetIndustries(List.of("F4521"));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(p));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);
        BigDecimal score = response.results().get(0).score();
        // industry partial 15 + region 20 + size 20 + age 15 + revenue 15 + cert 5 + keyword 1 = 91
        assertThat(score).isEqualByComparingTo("91.00");
    }

    @Test
    @DisplayName("REGION 광역 일치 (앞 2자리) → 14점")
    void match_regionBroadMatch_partialScore() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        CompanyMatchInput profile = sampleProfile();
        profile.setRegionCodes(List.of("11999"));  // 11 광역
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(profile));

        PolicyProgram p = fullMatchProgram();
        p.setTargetRegions(List.of("11000"));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(p));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);
        // industry 30 + region 14 + size 20 + age 15 + revenue 15 + cert 5 + keyword 1 = 100
        assertThat(response.results().get(0).score()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("SIZE ±20% 허용 → 16점")
    void match_sizeWithinTolerance_partialScore() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        CompanyMatchInput profile = sampleProfile();
        profile.setEmployeeCount(8);  // min=10 → 8 = -20%
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(profile));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(fullMatchProgram()));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);
        // industry 30 + region 20 + size 16 (tolerance) + age 15 + revenue 15 + cert 5 + keyword 1 = 100 (capped)
        assertThat(response.results().get(0).score()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("REVENUE 범위 외 + ±20% 외 → 0점")
    void match_revenueOutOfRange_zeroDimension() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        CompanyMatchInput profile = sampleProfile();
        profile.setAnnualRevenue(100_000_000_000L);  // max(10B) 의 10배
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(profile));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(fullMatchProgram()));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);
        // industry 30 + region 20 + size 20 + age 15 + revenue 0 + cert 5 + keyword 1 = 91
        assertThat(response.results().get(0).score()).isEqualByComparingTo("91.00");
    }

    // ─── REQ-POLICY-003-D-2: 등급 ─────────────────────────────────────────

    @Test
    @DisplayName("등급 A: score >= 80")
    void grade_A_threshold() {
        assertThat(service.computeGrade(new BigDecimal("80.00"))).isEqualTo("A");
        assertThat(service.computeGrade(new BigDecimal("100.00"))).isEqualTo("A");
    }

    @Test
    @DisplayName("등급 B: 60 <= score < 80")
    void grade_B_threshold() {
        assertThat(service.computeGrade(new BigDecimal("79.99"))).isEqualTo("B");
        assertThat(service.computeGrade(new BigDecimal("60.00"))).isEqualTo("B");
    }

    @Test
    @DisplayName("등급 C: 40 <= score < 60")
    void grade_C_threshold() {
        assertThat(service.computeGrade(new BigDecimal("59.99"))).isEqualTo("C");
        assertThat(service.computeGrade(new BigDecimal("40.00"))).isEqualTo("C");
    }

    @Test
    @DisplayName("등급 D: score < 40")
    void grade_D_threshold() {
        assertThat(service.computeGrade(new BigDecimal("39.99"))).isEqualTo("D");
        assertThat(service.computeGrade(BigDecimal.ZERO)).isEqualTo("D");
    }

    // ─── score_breakdown JSONB ─────────────────────────────────────────────

    @Test
    @DisplayName("score_breakdown JSON 에 5개 차원 + 보너스 포함")
    void scoreBreakdown_includesAllDimensions() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(fullMatchProgram()));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);
        String breakdown = response.results().get(0).scoreBreakdown();

        assertThat(breakdown).contains("\"industry\"");
        assertThat(breakdown).contains("\"region\"");
        assertThat(breakdown).contains("\"size\"");
        assertThat(breakdown).contains("\"age\"");
        assertThat(breakdown).contains("\"revenue\"");
        assertThat(breakdown).contains("\"cert_bonus\"");
        assertThat(breakdown).contains("\"keyword_bonus\"");
    }

    // ─── REQ-POLICY-003-D-3: TOP N + 정렬 ─────────────────────────────────

    @Test
    @DisplayName("TOP N=2 — 점수 내림차순 정렬, 상위 2건만 반환")
    void topN_sortsByScoreDesc() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));

        PolicyProgram a = fullMatchProgram();  // score 100
        a.setId(101L);
        PolicyProgram b = fullMatchProgram();  // score 100 (동일)
        b.setId(102L);
        b.setTargetRegions(List.of("99999"));  // region 0 → 80점
        PolicyProgram c = fullMatchProgram();
        c.setId(103L);
        c.setTargetIndustries(List.of("Z9999"));  // industry 0 → 70점
        c.setTargetRegions(List.of("99999"));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(a, b, c));

        PolicyMatchResponse response = service.matchForCompany(10L, 2);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).score())
                .isGreaterThanOrEqualTo(response.results().get(1).score());
    }

    @Test
    @DisplayName("clampTopN — 1 미만은 10, 50 초과는 50")
    void clampTopN_bounds() {
        assertThat(service.clampTopN(0)).isEqualTo(10);
        assertThat(service.clampTopN(-5)).isEqualTo(10);
        assertThat(service.clampTopN(100)).isEqualTo(50);
        assertThat(service.clampTopN(7)).isEqualTo(7);
    }

    // ─── REQ-POLICY-003-D-4: 7일 캐시 TTL ─────────────────────────────────

    @Test
    @DisplayName("캐시 hit 시 fromCache=true 반환, DB 매칭 미실행")
    void cacheHit_returnsFromCacheTrue() {
        PolicyMatchScore cache = PolicyMatchScore.builder()
                .id(1L).companyId(10L).policyId(100L)
                .score(new BigDecimal("85.00")).grade("A")
                .scoreBreakdown("{\"industry\":30}")
                .matchedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(7 * 86400))
                .build();
        List<PolicyMatchScore> cacheList = new ArrayList<>();
        for (int i = 0; i < 5; i++) cacheList.add(cache);
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), eq(5))).thenReturn(cacheList);
        when(programMapper.findById(100L)).thenReturn(Optional.of(fullMatchProgram()));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.fromCache()).isTrue();
        assertThat(response.results()).hasSize(5);
        verify(programMapper, never()).findActiveForMatching();
        verify(companyInputMapper, never()).findByCompanyId(anyLong());
    }

    @Test
    @DisplayName("캐시 부족 시 신규 매칭 실행 + 캐시 저장")
    void cacheMiss_runsMatchingAndSavesCache() {
        when(scoreMapper.findActiveCacheByCompanyId(eq(10L), anyInt())).thenReturn(List.of());
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));
        when(programMapper.findActiveForMatching()).thenReturn(List.of(fullMatchProgram()));

        PolicyMatchResponse response = service.matchForCompany(10L, 5);

        assertThat(response.fromCache()).isFalse();
        assertThat(response.results()).hasSize(1);
        verify(scoreMapper, times(1)).insert(any());
    }

    // ─── 프로필 UPSERT + 캐시 무효화 ───────────────────────────────────────

    @Test
    @DisplayName("upsertCompanyProfile — 신규 INSERT, 캐시 무효화")
    void upsert_insert_invalidatesCache() {
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.empty());

        CompanyProfileUpsertRequest req = new CompanyProfileUpsertRequest(
                10L, List.of("F4521"), List.of("11000"),
                50, 5_000_000_000L, 60, List.of("ISO9001"), null
        );

        service.upsertCompanyProfile(req);

        verify(companyInputMapper, times(1)).insert(any());
        verify(companyInputMapper, never()).update(any());
        verify(scoreMapper, times(1)).deleteByCompanyId(10L);
    }

    @Test
    @DisplayName("upsertCompanyProfile — 기존 UPDATE, 캐시 무효화")
    void upsert_update_invalidatesCache() {
        when(companyInputMapper.findByCompanyId(10L)).thenReturn(Optional.of(sampleProfile()));

        CompanyProfileUpsertRequest req = new CompanyProfileUpsertRequest(
                10L, List.of("F4521"), List.of("11000"),
                100, 8_000_000_000L, 80, List.of("ISO27001"), null
        );

        service.upsertCompanyProfile(req);

        verify(companyInputMapper, never()).insert(any());
        verify(companyInputMapper, times(1)).update(any());
        verify(scoreMapper, times(1)).deleteByCompanyId(10L);
    }

    // ─── 키워드 추출 (custom_attrs) ────────────────────────────────────────

    @Test
    @DisplayName("custom_attrs 의 keywords 배열 추출 → 정확한 키워드 리스트 반환")
    void extractKeywords_validJson_returnsList() {
        List<String> kws = service.extractKeywords("{\"keywords\":[\"창업\",\"R&D\",\"AI\"]}");
        assertThat(kws).containsExactly("창업", "R&D", "AI");
    }

    @Test
    @DisplayName("custom_attrs 빈 keywords → 빈 리스트")
    void extractKeywords_emptyArray_returnsEmpty() {
        List<String> kws = service.extractKeywords("{\"keywords\":[]}");
        assertThat(kws).isEmpty();
    }

    @Test
    @DisplayName("custom_attrs keywords 누락 → 빈 리스트")
    void extractKeywords_noKey_returnsEmpty() {
        List<String> kws = service.extractKeywords("{\"foo\":\"bar\"}");
        assertThat(kws).isEmpty();
    }

    @Test
    @DisplayName("withinTolerance — min/max ±20% 외에는 false")
    void withinTolerance_outsideRange() {
        assertThat(service.withinTolerance(8, 10, 100, 0.20)).isTrue();   // min*0.8 = 8 (포함)
        assertThat(service.withinTolerance(120, 10, 100, 0.20)).isTrue(); // max*1.2 = 120 (포함)
        assertThat(service.withinTolerance(5, 10, 100, 0.20)).isFalse();  // -50% (제외)
        assertThat(service.withinTolerance(150, 10, 100, 0.20)).isFalse();// +50% (제외)
    }
}
