package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.ProfileResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileUpsertRequest;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyMatchResultMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAFETY-002: 기업 안전 프로필 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanySafetyProfileServiceImpl — REQ-SAFETY-002")
class CompanySafetyProfileServiceImplTest {

    @Mock
    private CompanySafetyProfileMapper profileMapper;

    @Mock
    private SafetyMatchResultMapper matchResultMapper;

    @InjectMocks
    private CompanySafetyProfileServiceImpl service;

    private ProfileUpsertRequest sampleRequest() {
        return new ProfileUpsertRequest(
                "F4521",
                "건설업",
                100,
                "고소작업",
                List.of("추락", "중장비"),
                "D"
        );
    }

    private CompanySafetyProfile existingProfile() {
        return CompanySafetyProfile.builder()
                .id(50L)
                .companyId(10L)
                .industryCode("F0000")
                .subIndustry("기존")
                .employeeCount(50)
                .primaryProcess("기존공정")
                .hazardFactors("[\"기존\"]")
                .riskGrade("C")
                .build();
    }

    // ──────────────────────────────────────────────
    // upsertProfile — INSERT 경로
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("프로필 미존재 시 신규 INSERT — 매핑된 응답 반환, 캐시 무효화 없음")
    void upsertProfile_insertWhenAbsent() {
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.empty());

        ProfileResponse response = service.upsertProfile(10L, sampleRequest());

        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper, times(1)).insert(captor.capture());
        verify(profileMapper, never()).update(any());
        verify(matchResultMapper, never()).deleteByProfileId(any());

        CompanySafetyProfile inserted = captor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(10L);
        assertThat(inserted.getIndustryCode()).isEqualTo("F4521");
        assertThat(inserted.getRiskGrade()).isEqualTo("D");
        assertThat(inserted.getHazardFactors()).contains("추락").contains("중장비");

        assertThat(response.companyId()).isEqualTo(10L);
        assertThat(response.industryCode()).isEqualTo("F4521");
        assertThat(response.hazardFactors()).containsExactly("추락", "중장비");
    }

    // ──────────────────────────────────────────────
    // upsertProfile — UPDATE 경로 + 캐시 무효화 (REQ-SAFETY-002-D-1)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("프로필 존재 시 UPDATE + 매칭 캐시 무효화 (RISK-S5)")
    void upsertProfile_updateAndInvalidateCacheWhenExists() {
        CompanySafetyProfile existing = existingProfile();
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(existing));

        ProfileResponse response = service.upsertProfile(10L, sampleRequest());

        verify(profileMapper, times(1)).update(existing);
        verify(profileMapper, never()).insert(any());
        verify(matchResultMapper, times(1)).deleteByProfileId(50L);

        assertThat(existing.getIndustryCode()).isEqualTo("F4521");
        assertThat(existing.getSubIndustry()).isEqualTo("건설업");
        assertThat(existing.getEmployeeCount()).isEqualTo(100);
        assertThat(existing.getPrimaryProcess()).isEqualTo("고소작업");
        assertThat(existing.getRiskGrade()).isEqualTo("D");
        assertThat(existing.getHazardFactors()).contains("추락").contains("중장비");

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.industryCode()).isEqualTo("F4521");
    }

    @Test
    @DisplayName("hazardFactors null 입력 — 빈 JSON 배열로 직렬화")
    void upsertProfile_nullHazards_serializesAsEmptyArray() {
        ProfileUpsertRequest req = new ProfileUpsertRequest(
                "F4521", "건설업", 10, "고소작업", null, "D"
        );
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.empty());

        ProfileResponse response = service.upsertProfile(10L, req);

        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper).insert(captor.capture());
        assertThat(captor.getValue().getHazardFactors()).isEqualTo("[]");
        assertThat(response.hazardFactors()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // getMyProfile
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("내 프로필 조회 — DB 결과를 응답으로 매핑")
    void getMyProfile_returnsResponse() {
        CompanySafetyProfile profile = CompanySafetyProfile.builder()
                .id(100L).companyId(10L)
                .industryCode("F4521").subIndustry("건설업")
                .employeeCount(50).primaryProcess("고소작업")
                .hazardFactors("[\"추락\",\"중장비\"]")
                .riskGrade("D")
                .build();
        when(profileMapper.findByCompanyId(10L)).thenReturn(Optional.of(profile));

        ProfileResponse response = service.getMyProfile(10L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.companyId()).isEqualTo(10L);
        assertThat(response.industryCode()).isEqualTo("F4521");
        assertThat(response.hazardFactors()).containsExactlyInAnyOrder("추락", "중장비");
    }

    @Test
    @DisplayName("내 프로필 조회 미존재 시 SafetyProfileNotFoundException")
    void getMyProfile_missing_throws() {
        when(profileMapper.findByCompanyId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(99L))
                .isInstanceOf(SafetyProfileNotFoundException.class);
    }

    // ──────────────────────────────────────────────
    // 헬퍼: toJsonArray / parseJsonArray
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("toJsonArray — null/empty → []")
    void toJsonArray_handlesNullAndEmpty() {
        assertThat(CompanySafetyProfileServiceImpl.toJsonArray(null)).isEqualTo("[]");
        assertThat(CompanySafetyProfileServiceImpl.toJsonArray(List.of())).isEqualTo("[]");
    }

    @Test
    @DisplayName("toJsonArray — 항목 직렬화 + 따옴표 escape")
    void toJsonArray_escapesQuotes() {
        String json = CompanySafetyProfileServiceImpl.toJsonArray(List.of("a", "b\"c"));
        assertThat(json).isEqualTo("[\"a\",\"b\\\"c\"]");
    }

    @Test
    @DisplayName("toJsonArray — null 원소 skip")
    void toJsonArray_skipsNullElements() {
        java.util.List<String> withNull = new java.util.ArrayList<>();
        withNull.add("a");
        withNull.add(null);
        withNull.add("b");
        String json = CompanySafetyProfileServiceImpl.toJsonArray(withNull);
        assertThat(json).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    @DisplayName("parseJsonArray — null/blank/[] → 빈 리스트")
    void parseJsonArray_handlesNullAndEmpty() {
        assertThat(CompanySafetyProfileServiceImpl.parseJsonArray(null)).isEmpty();
        assertThat(CompanySafetyProfileServiceImpl.parseJsonArray("")).isEmpty();
        assertThat(CompanySafetyProfileServiceImpl.parseJsonArray("[]")).isEmpty();
    }

    @Test
    @DisplayName("parseJsonArray — 정상 입력 → 리스트")
    void parseJsonArray_parsesNormalInput() {
        List<String> parsed = CompanySafetyProfileServiceImpl.parseJsonArray("[\"추락\",\"중장비\"]");
        assertThat(parsed).containsExactly("추락", "중장비");
    }
}
