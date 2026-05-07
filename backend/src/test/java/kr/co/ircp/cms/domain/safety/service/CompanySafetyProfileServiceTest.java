package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.ProfileResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileUpsertRequest;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyMatchResultMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompanySafetyProfileService GREEN 단계 테스트.
 * REQ-SAFETY-002-D-1: 프로필 변경 시 매칭 캐시 무효화 (RISK-S5).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanySafetyProfileService GREEN 테스트 (REQ-SAFETY-002-D-1)")
class CompanySafetyProfileServiceTest {

    @Mock
    private CompanySafetyProfileMapper profileMapper;

    @Mock
    private SafetyMatchResultMapper matchResultMapper;

    private CompanySafetyProfileService service;

    @BeforeEach
    void setUp() {
        service = new CompanySafetyProfileServiceImpl(profileMapper, matchResultMapper);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private CompanySafetyProfile stubProfile(long id, long companyId, String hazardJson) {
        return CompanySafetyProfile.builder()
                .id(id)
                .companyId(companyId)
                .industryCode("F4521")
                .subIndustry("토목")
                .employeeCount(50)
                .primaryProcess("타워크레인 작업")
                .hazardFactors(hazardJson)
                .riskScore(new BigDecimal("75.50"))
                .riskGrade("D")
                .updatedAt(Instant.now())
                .build();
    }

    private ProfileUpsertRequest sampleRequest(List<String> hazards) {
        return new ProfileUpsertRequest(
                "F4521", "토목", 50, "타워크레인 작업", hazards, "D"
        );
    }

    // ──────────────────────────────────────────────
    // upsertProfile - INSERT 분기
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("프로필 upsert — 기존 프로필 없으면 mapper.insert 호출 (INSERT 분기)")
    void upsertProfile_noExistingProfile_callsInsert() {
        // arrange
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.empty());
        ProfileUpsertRequest req = sampleRequest(List.of("고소작업", "유해물질"));

        // act
        ProfileResponse result = service.upsertProfile(100L, req);

        // assert
        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper).insert(captor.capture());
        CompanySafetyProfile inserted = captor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(100L);
        assertThat(inserted.getIndustryCode()).isEqualTo("F4521");
        assertThat(inserted.getSubIndustry()).isEqualTo("토목");
        assertThat(inserted.getEmployeeCount()).isEqualTo(50);
        assertThat(inserted.getPrimaryProcess()).isEqualTo("타워크레인 작업");
        assertThat(inserted.getRiskGrade()).isEqualTo("D");
        // hazardFactors는 JSON 직렬화된 형태여야 함
        assertThat(inserted.getHazardFactors()).isEqualTo("[\"고소작업\",\"유해물질\"]");

        assertThat(result.industryCode()).isEqualTo("F4521");
        assertThat(result.hazardFactors()).containsExactly("고소작업", "유해물질");
        // INSERT 분기에서는 매칭 캐시 무효화 호출되지 않음
        verify(matchResultMapper, never()).deleteByProfileId(any());
    }

    @Test
    @DisplayName("프로필 upsert — INSERT 분기는 응답의 hazardFactors가 요청값 그대로")
    void upsertProfile_insert_responseContainsRequestHazards() {
        // arrange
        when(profileMapper.findByCompanyId(101L)).thenReturn(Optional.empty());
        ProfileUpsertRequest req = sampleRequest(List.of("화학물질"));

        // act
        ProfileResponse result = service.upsertProfile(101L, req);

        // assert
        assertThat(result.companyId()).isEqualTo(101L);
        assertThat(result.hazardFactors()).containsExactly("화학물질");
    }

    @Test
    @DisplayName("프로필 upsert — hazardFactors=null이면 빈 JSON 배열 [] 로 직렬화")
    void upsertProfile_nullHazards_serializesToEmptyArray() {
        // arrange
        when(profileMapper.findByCompanyId(102L)).thenReturn(Optional.empty());
        ProfileUpsertRequest req = sampleRequest(null);

        // act
        service.upsertProfile(102L, req);

        // assert
        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper).insert(captor.capture());
        assertThat(captor.getValue().getHazardFactors()).isEqualTo("[]");
    }

    @Test
    @DisplayName("프로필 upsert — hazardFactors=빈 리스트면 [] 로 직렬화")
    void upsertProfile_emptyHazards_serializesToEmptyArray() {
        // arrange
        when(profileMapper.findByCompanyId(103L)).thenReturn(Optional.empty());
        ProfileUpsertRequest req = sampleRequest(List.of());

        // act
        service.upsertProfile(103L, req);

        // assert
        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper).insert(captor.capture());
        assertThat(captor.getValue().getHazardFactors()).isEqualTo("[]");
    }

    @Test
    @DisplayName("프로필 upsert — hazardFactors에 null/빈 문자열 포함 시 JSON 직렬화에서 무시")
    void upsertProfile_nullElementInHazards_skipsNullEntries() {
        // arrange
        when(profileMapper.findByCompanyId(104L)).thenReturn(Optional.empty());
        // null 포함된 리스트
        ProfileUpsertRequest req = sampleRequest(Arrays.asList("정상값", null, "다른값"));

        // act
        service.upsertProfile(104L, req);

        // assert — null은 스킵, 정상값/다른값만 직렬화
        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper).insert(captor.capture());
        assertThat(captor.getValue().getHazardFactors()).isEqualTo("[\"정상값\",\"다른값\"]");
    }

    @Test
    @DisplayName("프로필 upsert — hazardFactors의 \" 문자는 백슬래시로 이스케이프")
    void upsertProfile_hazardWithDoubleQuotes_escapesProperly() {
        // arrange
        when(profileMapper.findByCompanyId(105L)).thenReturn(Optional.empty());
        ProfileUpsertRequest req = sampleRequest(List.of("위험\"인자"));

        // act
        service.upsertProfile(105L, req);

        // assert
        ArgumentCaptor<CompanySafetyProfile> captor = ArgumentCaptor.forClass(CompanySafetyProfile.class);
        verify(profileMapper).insert(captor.capture());
        assertThat(captor.getValue().getHazardFactors()).isEqualTo("[\"위험\\\"인자\"]");
    }

    // ──────────────────────────────────────────────
    // upsertProfile - UPDATE 분기 + 캐시 무효화
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("프로필 upsert — 기존 프로필 있으면 mapper.update + 캐시 무효화 순서로 호출 (UPDATE 분기)")
    void upsertProfile_existingProfile_updatesAndInvalidatesCache() {
        // arrange
        CompanySafetyProfile existing = stubProfile(7L, 200L, "[\"기존인자\"]");
        when(profileMapper.findByCompanyId(200L)).thenReturn(Optional.of(existing));
        ProfileUpsertRequest req = new ProfileUpsertRequest(
                "C2511", "제조", 100, "용접", List.of("화학물질"), "C"
        );

        // act
        ProfileResponse result = service.upsertProfile(200L, req);

        // assert — 호출 순서 검증
        InOrder order = inOrder(profileMapper, matchResultMapper);
        order.verify(profileMapper).update(any(CompanySafetyProfile.class));
        order.verify(matchResultMapper).deleteByProfileId(7L);

        // 기존 엔티티가 수정된 값으로 반영되었는지 검증
        assertThat(existing.getIndustryCode()).isEqualTo("C2511");
        assertThat(existing.getSubIndustry()).isEqualTo("제조");
        assertThat(existing.getEmployeeCount()).isEqualTo(100);
        assertThat(existing.getPrimaryProcess()).isEqualTo("용접");
        assertThat(existing.getRiskGrade()).isEqualTo("C");
        assertThat(existing.getHazardFactors()).isEqualTo("[\"화학물질\"]");

        // INSERT 호출되지 않음
        verify(profileMapper, never()).insert(any());
        // 응답값 검증
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.hazardFactors()).containsExactly("화학물질");
    }

    @Test
    @DisplayName("프로필 upsert — UPDATE 분기 시 매칭 캐시는 기존 프로필 ID로 삭제")
    void upsertProfile_update_invalidatesCacheByExistingProfileId() {
        // arrange
        CompanySafetyProfile existing = stubProfile(99L, 300L, "[]");
        when(profileMapper.findByCompanyId(300L)).thenReturn(Optional.of(existing));

        // act
        service.upsertProfile(300L, sampleRequest(List.of("updated")));

        // assert — companyId가 아닌 profileId(99L)로 삭제
        verify(matchResultMapper).deleteByProfileId(99L);
    }

    // ──────────────────────────────────────────────
    // getMyProfile
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getMyProfile — 존재하는 프로필 조회 시 hazardFactors 역직렬화 후 반환")
    void getMyProfile_existingProfile_returnsResponseWithDeserializedHazards() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "[\"고소작업\",\"유해물질\"]");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));

        // act
        ProfileResponse result = service.getMyProfile(100L);

        // assert
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.companyId()).isEqualTo(100L);
        assertThat(result.industryCode()).isEqualTo("F4521");
        assertThat(result.riskGrade()).isEqualTo("D");
        assertThat(result.hazardFactors()).containsExactly("고소작업", "유해물질");
    }

    @Test
    @DisplayName("getMyProfile — hazardFactors가 빈 JSON 배열이면 빈 리스트 반환")
    void getMyProfile_emptyHazardArray_returnsEmptyList() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "[]");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));

        // act
        ProfileResponse result = service.getMyProfile(100L);

        // assert
        assertThat(result.hazardFactors()).isEmpty();
    }

    @Test
    @DisplayName("getMyProfile — hazardFactors=null이면 빈 리스트 반환")
    void getMyProfile_nullHazards_returnsEmptyList() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, null);
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));

        // act
        ProfileResponse result = service.getMyProfile(100L);

        // assert
        assertThat(result.hazardFactors()).isEmpty();
    }

    @Test
    @DisplayName("getMyProfile — 존재하지 않는 프로필은 SafetyProfileNotFoundException")
    void getMyProfile_nonExistentProfile_throwsSafetyProfileNotFoundException() {
        // arrange
        when(profileMapper.findByCompanyId(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> service.getMyProfile(999L))
                .isInstanceOf(SafetyProfileNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getMyProfile — 공백 hazardFactors 문자열도 빈 리스트로 처리")
    void getMyProfile_blankHazards_returnsEmptyList() {
        // arrange
        CompanySafetyProfile profile = stubProfile(7L, 100L, "   ");
        when(profileMapper.findByCompanyId(100L)).thenReturn(Optional.of(profile));

        // act
        ProfileResponse result = service.getMyProfile(100L);

        // assert
        assertThat(result.hazardFactors()).isEmpty();
    }
}
